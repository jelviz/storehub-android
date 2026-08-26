# StoreHub V6 Local — Android Only

این نسخه عمداً **هیچ Backend، VPS، SQL Server یا پنل وبی ندارد**.

## معماری

Android App → Room local database
Android App → WooCommerce REST API فقط برای سینک کاتالوگ

تمام عملیات فروشگاه داخل گوشی انجام می‌شود و بدون اینترنت هم کار می‌کند؛ فقط سینک WooCommerce اینترنت می‌خواهد.

## امکانات
- محصولات دستی و محصولات سینک‌شده از WooCommerce
- موجودی مستقل مغازه و دپو
- فعال‌سازی انتخابی محصول برای مغازه
- تعدیل موجودی و تاریخچه ورود/خروج
- صندوق فروش و اسکن Barcode/QR با دوربین
- فروش و مرجوعی تعدادی
- انتقال دپو → مغازه در دو مرحله خروج/دریافت
- ثبت خرید بازار و افزایش موجودی پس از دریافت
- چک‌های صادرشده، سررسید شمسی و Notification
- قرار ملاقات و Notification
- تقویم شمسی
- چاپ محلی لیبل QR با Android Print Service
- بکاپ و Restore فایل JSON
- سینک دستی و خودکار WooCommerce

## اتصال WooCommerce
در خود اپ: تنظیمات → اتصال مستقیم به WooCommerce

- Base URL: `https://example.com`
- API version: `wc/v3`
- Consumer Key: `ck_...`
- Consumer Secret: `cs_...`

کلید و Secret با Android Keystore روی همان گوشی رمزگذاری می‌شوند.

اپ از سرویس زیر استفاده می‌کند:
`GET /wp-json/wc/v3/products`

موجودی WooCommerce عمداً وارد موجودی محلی StoreHub نمی‌شود.

### نکته هاست
حالت پیش‌فرض HTTP Basic Auth روی HTTPS است. اگر هاست Authorization Header را حذف کند، در تنظیمات گزینه Query-string auth را فقط به عنوان fallback فعال کن.

## بکاپ
Settings → گرفتن فایل بکاپ

فایل JSON شامل اطلاعات StoreHub است ولی Consumer Key/Secret داخل بکاپ قرار نمی‌گیرد. بعد از Restore روی گوشی جدید، کلیدهای WooCommerce را دوباره وارد کن.

## ساخت APK بدون Android Studio
پوشه `StoreHubAndroid` را در GitHub قرار بده و از Actions، workflow `Build StoreHub Local APK` را اجرا کن. APK در Artifacts قرار می‌گیرد.

## نکته امنیتی مهم
چون بک‌اند حذف شده، Consumer Key/Secret ناچار روی گوشی نگهداری می‌شوند. برای کاهش ریسک:
- فقط HTTPS استفاده کن.
- برای کلید WooCommerce دسترسی Read کافی است چون StoreHub فقط کاتالوگ را می‌خواند.
- قفل صفحه/اثر انگشت گوشی را فعال نگه دار.
- از فایل بکاپ به طور منظم نسخه جدا نگه دار.
