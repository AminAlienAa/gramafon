گرامافون: برنامه‌ی ویندوز و اندروید
====================================

این بسته‌ها فقط پنجره‌ی سایت خودتن. هر آپدیتی که روی سایت بدی، توی برنامه هم میاد.

قدم‌ها (یک بار):
1) محتوای این zip رو کنار index.html توی ریپوی گیت‌هاب آپلود کن (پوشه‌های desktop و android و .github).
   اگه .github با درگ کردن نیومد: Add file > Create new file و اسمش رو
   .github/workflows/build-windows.yml بنویس و محتوا رو کپی کن. همین کار رو برای build-android.yml بکن.
2) توی ریپو: Settings > Secrets and variables > Actions > تب Variables > New repository variable
   اسم: SITE_URL   مقدار: آدرس سایتت (مثلاً https://example.com/)
3) تب Actions:
   - Build Windows installer > Run workflow   (خروجی: Gramafon-Windows-Setup)
   - Build Android APK > Run workflow         (خروجی: Gramafon-Android-APK)
   بعد از چند دقیقه، پایین صفحه‌ی اجرا (Artifacts) فایل‌ها رو دانلود کن.

ویندوز: فایل exe رو اجرا کن. هشدار SmartScreen: More info > Run anyway.
اندروید: apk رو روی گوشی باز کن و اجازه‌ی نصب از این منبع رو بده.
