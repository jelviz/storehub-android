# DINAL StoreHub V16 — معماری

## هسته محلی
- Kotlin + Jetpack Compose
- Room برای دیتای فروشگاه
- WorkManager/AlarmManager برای کارهای زمان‌بندی‌شده
- Android Keystore برای credentialهای WooCommerce/OpenAI

## موجودی (V16.4)
StoreHub مرجع موجودی است. هر انبار OnHand / Reserved / Damaged / InTransit دارد.
Available = OnHand - Reserved - Damaged.
تغییر موجودی فقط با Inventory Transaction است. کانال آنلاین موجودی را تعیین نمی‌کند.

## WooCommerce
Android App → HTTPS → WooCommerce REST API

WooCommerce منبع کاتالوگ و مقصد انتشار محصول است. موجودی مغازه در Room مستقل می‌ماند و در Phase 2 از روی ChannelInventoryPolicy به کانال‌ها push می‌شود.

## DINAL Assistant
Android App → OpenAI Responses API

در حالت «استفاده از اطلاعات محلی»، اپ ابتدا یک context محدود و مرتبط از Room می‌سازد؛ شامل داشبورد، موجودی‌های مهم، چک‌های باز، فروش/خرید اخیر و قرارهای باز. سپس فقط همان context به همراه سؤال ارسال می‌شود.

این قابلیت به اپ ChatGPT نصب‌شده روی گوشی یا Memory/Conversationهای ChatGPT دسترسی ندارد.

## تصویر ثبت هوشمند
Camera/Gallery → WebP

در صورت آماده بودن optional ML Kit Subject Segmentation:
Camera/Gallery → segmentation → white background → WebP

اگر مدل آماده نباشد:
Camera/Gallery → WebP fallback → ادامه AI/Publish

بنابراین optional ML Kit دیگر یک dependency مسدودکننده نیست.

## چرخش صفحه
MainActivity با `configChanges=orientation|screenSize|keyboardHidden` از recreation معمول هنگام چرخش جلوگیری می‌کند. فرم‌های مهم نیز stateهای متنی قابل ذخیره دارند.
