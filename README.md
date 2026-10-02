# مراقب Spring Boot

لوحة عربية خفيفة لمراقبة أخطاء وزمن معالجة طلبات Spring Boot. يعتمد القياس على `System.nanoTime()` ويعرض الزمن بالنانوثانية.

## التشغيل

```bash
cd spring-monitor
./mvnw spring-boot:run
```

ثم افتح <http://localhost:8080>.

## الاستضافة الذاتية باستخدام Docker Compose

يتطلب Docker Engine مع إضافة Compose. انسخ ملف الإعداد، أنشئ كلمة مرور عشوائية بطول 32 بايت على الأقل، وضعها في `MONITOR_AUTH_PASSWORD` داخل `.env`:

```bash
cp .env.example .env
openssl rand -hex 32
mkdir -p spring-monitor/workspace
```

بعد إدخال كلمة المرور في `.env`، شغّل الخدمة:

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f spring-monitor
```

افتراضياً يستمع التطبيق على `127.0.0.1:8080`؛ استخدم reverse proxy على الخادم لإنهاء HTTPS، ولا تفتح المنفذ مباشرة على الإنترنت دون TLS وجدار ناري. بيانات `.env` سرية ومستثناة من Git. مجلد مساحة العمل قابل للقراءة فقط داخل الحاوية؛ غيّر `MONITOR_WORKSPACE_PATH` إلى مجلد المشاريع الذي تريد اكتشافه. سجل الطلبات ومشاكل IDE يبقيان في الذاكرة ويُفقدان عند إعادة تشغيل التطبيق.

يمكن تشغيل الخادم عبر اتصال خلوي مثل Huawei E177 إذا كان Linux يتعرف على المودم كواجهة شبكة مستقرة. هذا لا يضمن إمكانية الوصول من الإنترنت: كثير من شرائح الجوال تستخدم CGNAT وتحجب الاتصالات الواردة. تحقق من وجود عنوان IP عام واتصالات واردة لدى المشغل، أو استخدم نفقاً عكسياً إلى خادم عام مع HTTPS.

## الاستقرار والتشغيل

يعرض Spring Boot فحوص الصحة على `/actuator/health/liveness` و`/actuator/health/readiness`. عند إيقاف طبيعي، ينتظر الخادم حتى 20 ثانية لتصريف الطلبات الجارية. لسياسة إعادة تشغيل عند تعطل العملية أو نشر بلا توقف، شغّل التطبيق تحت systemd أو Kubernetes واستخدم أكثر من نسخة مع موازن حمل.

هذا لا يصلح أخطاء الكود تلقائيًا ولا يضمن إصلاحًا خلال نانوثانية؛ النانوثانية وحدة قياس زمن الطلب فقط. سجل الطلبات والمشاكل الحالي محفوظ في الذاكرة، لذا يلزم تخزين خارجي مشترك واستعادة احتياطية لتشغيل متعدد النسخ.

### صيانة التبعيات والتحقق المستمر

يراقب Dependabot تحديثات Maven وGitHub Actions أسبوعيًا ويرسلها كطلبات مراجعة. يفحص GitHub Actions البناء والاختبارات على Java 17 و25، ويتحقق من صياغة JavaScript في كل push وpull request. راجع نتائج الاختبارات وملاحظات الإصدار قبل الدمج والنشر؛ لا تدمج تحديثات الإصدارات الرئيسية تلقائيًا. هذا يقلل مخاطر التقادم لكنه لا يضمن توافقًا أو إصلاحًا آليًا دائمًا.

### نشر Ubuntu Server عبر systemd

ملف الخدمة الجاهز هو `deploy/spring-monitor.service`. من جذر المستودع ابنِ التطبيق ثم جهّز المستخدم ومجلد النشر:

```bash
sudo apt update
sudo apt install -y openjdk-17-jre-headless
cd spring-monitor
./mvnw -DskipTests package
sudo useradd --system --home /opt/spring-monitor --shell /usr/sbin/nologin spring-monitor
sudo install -d -o spring-monitor -g spring-monitor /opt/spring-monitor
sudo install -m 0644 target/spring-monitor-0.0.1-SNAPSHOT.jar /opt/spring-monitor/spring-monitor.jar
sudo install -d -o root -g spring-monitor -m 0750 /etc/spring-monitor
sudo install -o root -g spring-monitor -m 0640 /dev/null /etc/spring-monitor/application.properties
sudoedit /etc/spring-monitor/application.properties
cd ..
sudo install -m 0644 deploy/spring-monitor.service /etc/systemd/system/spring-monitor.service
sudo systemctl daemon-reload
sudo systemctl enable --now spring-monitor
```

أضف إلى `/etc/spring-monitor/application.properties` اسم المستخدم وكلمة مرور عشوائية لا تقل عن 24 حرفاً (`monitor.auth.username` و`monitor.auth.password`) قبل بدء الخدمة؛ يحميها مالك root ومجموعة الخدمة.

تحقق من الحالة والصحة باستخدام `systemctl status spring-monitor` و`curl http://localhost:8080/actuator/health/readiness`. تعيد الخدمة التشغيل عند الفشل، وتحدّ إعادة المحاولة بخمس مرات خلال دقيقة لتجنب حلقة انهيار مستمرة. هذه خدمة نسخة واحدة وليست إعداد توافر عالٍ؛ صفر توقف يتطلب عدة نسخ خلف موازن حمل وتخزينًا مشتركًا.

تسجل اللوحة طلبات `/api/*` باستثناء واجهات المراقبة نفسها، وتحتفظ بآخر 500 طلب في الذاكرة. يمكن تجربة القياس وخطأ اختباري من الواجهة.

تعرض كذلك استخدام CPU والذاكرة ومساحة القرص ومعدل I/O لعملية Java وبيانات اللوحة الأم وحساسات الحرارة والمراوح التي يكشفها النظام. زمن الاتصال هو RTT إلى التطبيق؛ وسرعة التنزيل تقدير من واجهة المتصفح، وقد لا تكون مدعومة. الحساسات المخفية بواسطة الأجهزة الافتراضية تظهر كغير متاحة.

واجهات المراقبة:

- `GET /api/monitor/summary`
- `GET /api/monitor/requests`
- `GET /api/monitor/errors`
- `GET /api/monitor/system`
- `GET /api/monitor/discovery`
- `GET /api/monitor/discovery?refresh=true`
- `GET /api/monitor/ide-issues?source=android-studio|xcode&limit=50`
- `POST /api/monitor/ide-issues`

ترسل أدوات البناء المشكلة إلى `POST /api/monitor/ide-issues` بصيغة JSON؛ القيم المدعومة للمصدر `android-studio` و`xcode`، وللشدة `error` و`warning` و`info`:

```bash
curl -X POST http://localhost:8080/api/monitor/ide-issues \
	-H 'Content-Type: application/json' \
	-d '{"source":"android-studio","project":"SampleApp","severity":"error","message":"Unresolved reference: viewModel","file":"app/src/main/java/Sample.kt","line":42,"code":"KOTLIN_COMPILATION_ERROR"}'
```

مساحة العمل الحالية لا تحتوي مشاريع Android أو Xcode ولا تثبيت Xcode، لذلك لا يوجد إرسال تلقائي من IDE بعد. اربط Gradle أو مرحلة بناء Xcode بهذا الـAPI لإرسال نتائج البناء؛ التخزين الحالي في الذاكرة لآخر 500 مشكلة، فلا تعرض الخدمة على شبكة عامة دون إضافة مصادقة.

يبحث الاكتشاف تلقائيًا داخل جذر مساحة العمل المحدد في `monitor.discovery.root` عن مشاريع Android Studio/Gradle وXcode/Swift وFlutter وReact Native وSpring Boot، ويتحقق من أدوات CLI الموجودة على `PATH`. الفحص محدود العمق والحجم، يستثني مجلدات البناء والكاش، ويُخزّن مؤقتًا 30 ثانية؛ زر «إعادة الفحص» يجبر فحصًا جديدًا. لا يبحث في كامل الجهاز ولا يكتشف التطبيقات العاملة خارج الحاوية.