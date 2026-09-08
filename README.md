# Maxxum 145 CVX — Android 360° Viewer

Bu repo, ekransız/minimal bir Android uygulamasının temelini içerir. Uygulama açıldığında tam ekran OpenGL ES görüntüsü gelir ve geçici traktör önizleme mesh'i kendi etrafında yavaşça döner.

## Traktör dosyaları
Orijinal GIANTS Engine asset'i şu konumdadır:

`app/src/main/assets/tractor_source/maxxum145CVX.zip`

ZIP içindeki ana dosyalar `.i3d`, `.i3d.shapes` ve `.ast` texture asset'leridir. Android renderer bunları doğrudan kullanmaz; hedef format **glTF/GLB** olacaktır.

## Build
Android Studio'da projeyi açın ve `app` modülünü çalıştırın. Ya da kökte Gradle wrapper oluşturup `./gradlew assembleDebug` kullanabilirsiniz.

> Not: Bu paket, gerçek `.i3d` modelini henüz GLB'ye dönüştürmüyor. Önizleme renderer'ı derlenebilir bir başlangıç noktasıdır. Sonraki adımda asset conversion + GLB yükleyicisi eklenmelidir.
