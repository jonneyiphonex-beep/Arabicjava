package dev.arabicjava.monitor;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.sun.management.OperatingSystemMXBean;
import org.springframework.stereotype.Service;

@Service
public class SystemTelemetryService {
	private static final Path HWMON_PATH = Path.of("/sys/class/hwmon");
	private static final Path THERMAL_PATH = Path.of("/sys/class/thermal");
	private static final Path PROCESS_IO_PATH = Path.of("/proc/self/io");
	private static final Path DMI_PATH = Path.of("/sys/class/dmi/id");

	private final OperatingSystemMXBean operatingSystem =
			(OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
	private long previousReadBytes = -1;
	private long previousWriteBytes = -1;
	private long previousIoSampleNanos = -1;

	public synchronized SystemTelemetry snapshot() {
		MemoryUsage memoryUsage = readMemoryUsage();
		long totalMemory = memoryUsage.totalBytes();
		long usedMemory = Math.max(0, totalMemory - memoryUsage.availableBytes());

		DiskUsage diskUsage = readDiskUsage();
		DiskThroughput throughput = readDiskThroughput();
		List<SensorReading> temperatures = readHwmonSensors("temp", "°C", true);
		if (temperatures.isEmpty()) {
			temperatures = readThermalZones();
		}

		return new SystemTelemetry(
				toPercent(operatingSystem.getCpuLoad()),
				Runtime.getRuntime().availableProcessors(),
				usedMemory,
				totalMemory,
				toPercent(totalMemory == 0 ? -1 : (double) usedMemory / totalMemory),
				diskUsage.usedBytes(),
				diskUsage.totalBytes(),
				toPercent(diskUsage.totalBytes() == 0 ? -1 : (double) diskUsage.usedBytes() / diskUsage.totalBytes()),
				throughput.readBytesPerSecond(),
				throughput.writeBytesPerSecond(),
				readBoardName(),
				List.copyOf(temperatures),
				List.copyOf(readHwmonSensors("fan", "RPM", false)),
				List.copyOf(readCoolingDevices()));
	}

	private DiskUsage readDiskUsage() {
		try {
			FileStore store = Files.getFileStore(Path.of("."));
			long total = store.getTotalSpace();
			return new DiskUsage(Math.max(0, total - store.getUsableSpace()), total);
		} catch (IOException | SecurityException exception) {
			return new DiskUsage(0, 0);
		}
	}

	private MemoryUsage readMemoryUsage() {
		Long totalKilobytes = null;
		Long availableKilobytes = null;
		try {
			for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
				String[] parts = line.split(":", 2);
				if (parts.length == 2 && (parts[0].equals("MemTotal") || parts[0].equals("MemAvailable"))) {
					long value = Long.parseLong(parts[1].trim().split("\\s+")[0]);
					if (parts[0].equals("MemTotal")) {
						totalKilobytes = value;
					} else {
						availableKilobytes = value;
					}
				}
			}
		} catch (IOException | NumberFormatException | SecurityException ignored) {
			return memoryFromOperatingSystem();
		}

		if (totalKilobytes == null || availableKilobytes == null) {
			return memoryFromOperatingSystem();
		}
		return new MemoryUsage(totalKilobytes * 1024, availableKilobytes * 1024);
	}

	private MemoryUsage memoryFromOperatingSystem() {
		return new MemoryUsage(
				Math.max(0, operatingSystem.getTotalMemorySize()),
				Math.max(0, operatingSystem.getFreeMemorySize()));
	}

	private DiskThroughput readDiskThroughput() {
		Map<String, Long> io = new HashMap<>();
		try {
			for (String line : Files.readAllLines(PROCESS_IO_PATH)) {
				String[] parts = line.split(":", 2);
				if (parts.length == 2 && (parts[0].equals("read_bytes") || parts[0].equals("write_bytes"))) {
					io.put(parts[0], Long.parseLong(parts[1].trim()));
				}
			}
		} catch (IOException | NumberFormatException | SecurityException exception) {
			return new DiskThroughput(null, null);
		}

		Long readBytes = io.get("read_bytes");
		Long writeBytes = io.get("write_bytes");
		long now = System.nanoTime();
		Double readRate = null;
		Double writeRate = null;
		if (readBytes != null && writeBytes != null && previousIoSampleNanos >= 0) {
			long elapsed = now - previousIoSampleNanos;
			if (elapsed > 0) {
				readRate = Math.max(0, readBytes - previousReadBytes) * 1_000_000_000.0 / elapsed;
				writeRate = Math.max(0, writeBytes - previousWriteBytes) * 1_000_000_000.0 / elapsed;
			}
		}
		if (readBytes != null && writeBytes != null) {
			previousReadBytes = readBytes;
			previousWriteBytes = writeBytes;
			previousIoSampleNanos = now;
		}
		return new DiskThroughput(readRate, writeRate);
	}

	private List<SensorReading> readHwmonSensors(String kind, String unit, boolean milliUnits) {
		List<SensorReading> readings = new ArrayList<>();
		if (!Files.isDirectory(HWMON_PATH)) {
			return readings;
		}
		try (Stream<Path> chips = Files.list(HWMON_PATH)) {
			for (Path chip : chips.toList()) {
				String chipName = readText(chip.resolve("name"));
				try (Stream<Path> files = Files.list(chip)) {
					for (Path input : files.filter(path -> path.getFileName().toString().matches(kind + "\\d+_input")).toList()) {
						String stem = input.getFileName().toString().replace("_input", "");
						String rawValue = readText(input);
						if (rawValue == null) {
							continue;
						}
						try {
							double value = Long.parseLong(rawValue) / (milliUnits ? 1000.0 : 1.0);
							String label = readText(chip.resolve(stem + "_label"));
							if (label == null) {
								label = (chipName == null ? "Sensor" : chipName) + " " + stem;
							}
							readings.add(new SensorReading(label, value, unit));
						} catch (NumberFormatException ignored) {
							continue;
						}
					}
				} catch (IOException | SecurityException ignored) {
					continue;
				}
			}
		} catch (IOException | SecurityException ignored) {
			return readings;
		}
		return readings;
	}

	private List<SensorReading> readThermalZones() {
		List<SensorReading> readings = new ArrayList<>();
		if (!Files.isDirectory(THERMAL_PATH)) {
			return readings;
		}
		try (Stream<Path> zones = Files.list(THERMAL_PATH)) {
			for (Path zone : zones.filter(path -> path.getFileName().toString().matches("thermal_zone\\d+")).toList()) {
				String rawValue = readText(zone.resolve("temp"));
				if (rawValue == null) {
					continue;
				}
				try {
					double value = Long.parseLong(rawValue) / 1000.0;
					String label = readText(zone.resolve("type"));
					readings.add(new SensorReading(label == null ? zone.getFileName().toString() : label, value, "°C"));
				} catch (NumberFormatException ignored) {
					continue;
				}
			}
		} catch (IOException | SecurityException ignored) {
			return readings;
		}
		return readings;
	}

	private List<SensorReading> readCoolingDevices() {
		List<SensorReading> readings = new ArrayList<>();
		if (!Files.isDirectory(THERMAL_PATH)) {
			return readings;
		}
		try (Stream<Path> devices = Files.list(THERMAL_PATH)) {
			for (Path device : devices.filter(path -> path.getFileName().toString().matches("cooling_device\\d+")).toList()) {
				try {
					double current = Double.parseDouble(readText(device.resolve("cur_state")));
					double maximum = Double.parseDouble(readText(device.resolve("max_state")));
					if (maximum > 0) {
						String label = readText(device.resolve("type"));
						readings.add(new SensorReading(label == null ? device.getFileName().toString() : label,
								current * 100.0 / maximum, "%"));
					}
				} catch (NumberFormatException | NullPointerException ignored) {
					continue;
				}
			}
		} catch (IOException | SecurityException ignored) {
			return readings;
		}
		return readings;
	}

	private String readBoardName() {
		String vendor = readText(DMI_PATH.resolve("board_vendor"));
		String name = readText(DMI_PATH.resolve("board_name"));
		if (name == null) {
			name = readText(DMI_PATH.resolve("product_name"));
		}
		if (vendor == null) {
			return name;
		}
		return name == null ? vendor : vendor + " · " + name;
	}

	private String readText(Path path) {
		try {
			String value = Files.readString(path).trim();
			return value.isEmpty() ? null : value;
		} catch (IOException | SecurityException exception) {
			return null;
		}
	}

	private Double toPercent(double ratio) {
		return Double.isFinite(ratio) && ratio >= 0 ? ratio * 100.0 : null;
	}

	private record DiskUsage(long usedBytes, long totalBytes) {
	}

	private record MemoryUsage(long totalBytes, long availableBytes) {
	}

	private record DiskThroughput(Double readBytesPerSecond, Double writeBytesPerSecond) {
	}
}