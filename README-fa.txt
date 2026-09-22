گرامافون: برنامه‌ی ویندوز، مک و اندروید
=========================================

این بسته‌ها فقط پنجره‌ی سایت خودتن. هر آپدیتی که روی سایت بدی، توی برنامه هم میاد.

قدم‌ها (یک بار):
1) محتوای این zip رو کنار index.html توی ریپوی گیت‌هاب آپلود کن (پوشه‌های desktop و android و .github).
   اگه .github با درگ کردن نیومد: Add file > Create new file و اسمش رو
   .github/workflows/build-windows.yml بنویس و محتوا رو کپی کن. همین کار رو برای build-android.yml و build-mac.yml بکن.
2) توی ریپو: Settings > Secrets and variables > Actions > تب Variables > New repository variable
   اسم: SITE_URL   مقدار: آدرس سایتت (مثلاً https://example.com/)
3) تب Actions و اجرای (Run workflow):
   - Build Windows installer  (خروجی: Gramafon-Windows-Setup)
   - Build Android APK        (خروجی: Gramafon-Android-APK)
   - Build macOS app          (خروجی: Gramafon-Mac، دو فایل dmg: arm64 برای M1/M2/M3 و x64 برای اینتل)
   بعد از چند دقیقه، پایین صفحه‌ی اجرا (Artifacts) فایل‌ها رو دانلود کن.

ویندوز: exe رو اجرا کن. هشدار SmartScreen: More info > Run anyway.
اندروید: apk رو روی گوشی باز کن و اجازه‌ی نصب از این منبع رو بده.
مک: dmg رو باز کن، برنامه رو بکش توی Applications، بار اول راست‌کلیک > Open.
