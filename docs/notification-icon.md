# Notification icon

Starlit Coffee notifications use a dedicated monochrome form of the app icon.
It preserves the rounded cup, open handle, and flame-shaped steam while removing
the launcher icon's night-sky background, colour, shading, texture, and small
painted details.

![Notification icon on dark and light system surfaces](assets/notification-icon-preview.png)

## Product role

- The launcher continues to use `@mipmap/ic_launcher`.
- Notifications use `@drawable/ic_notification_starlit` exclusively.
- Android renders the alpha mask with the system's notification tint, so visible
  resource pixels are pure white and the background is transparent.
- The 24 dp canvas is packaged at 24, 36, 48, 72, and 96 pixels for the mdpi
  through xxxhdpi density buckets.

All `NotificationCompat.Builder` instances apply the icon through
`withStarlitSmallIcon()` in `NotificationAppearance.kt`. The architecture test
fails if a builder bypasses that shared appearance or if an asset has the wrong
dimensions, loses transparency, or contains a visible non-white pixel.

## Design sources

- `assets/notification-icon-imagegen-source.png` is the selected ImageGen design.
- `assets/notification-icon-monochrome-master.png` is the normalized 512 px white
  alpha master from which the Android density assets are derived.
- `assets/notification-icon-preview.png` previews the production alpha mask with
  representative light and dark system tints.
