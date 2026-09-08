# Model conversion plan

1. `maxxum145CVX.i3d` içindeki `<File>` ve `<Material>` referanslarını çıkart.
2. `.i3d.shapes` mesh verisini GIANTS uyumlu bir importer veya Blender/assimp tabanlı araçla glTF'ye aktar.
3. Sadece gerçekten kullanılan texture'ları paketle.
4. Büyük/tekrarlı texture'ları Android için ETC2/ASTC'ye uygun hale getir.
5. Tek bir `maxxum145CVX.glb` (veya mesh + texture klasörü) oluştur.
6. `TractorRenderer` içindeki geçici procedural mesh'i gerçek GLB yükleyicisiyle değiştir.
