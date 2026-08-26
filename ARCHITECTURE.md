# StoreHub V6 — معماری نهایی Android-only

## چیزی که حذف شده
- ASP.NET Core Backend
- VPS / Cloud Server
- SQL Server / SQLite Server
- دامنه و HTTPS مربوط به StoreHub
- Login و API Token مربوط به StoreHub
- پنل وب

## چیزی که باقی مانده
- اپ Android
- Room database روی خود گوشی
- WorkManager برای Reminder و Woo sync
- Android Keystore برای Consumer Key/Secret
- اتصال مستقیم HTTPS به WooCommerce REST API
- فایل Backup دستی JSON

## مسیر داده
فروش/انبار/چک/خرید/قرار → Room روی گوشی

سینک کاتالوگ → گوشی → HTTPS → WooCommerce `GET /wp-json/wc/v3/products`

هیچ موجودی از WooCommerce روی موجودی محلی اعمال نمی‌شود.
