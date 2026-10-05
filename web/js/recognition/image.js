// Image helpers: decode a File/Blob into a canvas, downscale, export JPEG.

export async function loadBitmap(blob) {
  if ('createImageBitmap' in window) {
    try {
      return await createImageBitmap(blob, { imageOrientation: 'from-image' });
    } catch { /* fall through */ }
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(blob);
    const img = new Image();
    img.onload = () => { URL.revokeObjectURL(url); resolve(img); };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Não foi possível ler a imagem')); };
    img.src = url;
  });
}

/** Draws the bitmap into a canvas no larger than maxSide on its longest side. */
export function toCanvas(bitmap, maxSide = 1600) {
  const w = bitmap.width || bitmap.naturalWidth;
  const h = bitmap.height || bitmap.naturalHeight;
  const scale = Math.min(1, maxSide / Math.max(w, h));
  const canvas = document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(w * scale));
  canvas.height = Math.max(1, Math.round(h * scale));
  canvas.getContext('2d', { willReadFrequently: true }).drawImage(bitmap, 0, 0, canvas.width, canvas.height);
  return canvas;
}

export function canvasToBlob(canvas, type = 'image/jpeg', quality = 0.86) {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality));
}

/** Prepares both the analysis canvas and the stored cover from one file. */
export async function prepareImage(file) {
  const bitmap = await loadBitmap(file);
  const analysis = toCanvas(bitmap, 1600);
  const cover = await canvasToBlob(toCanvas(bitmap, 1024));
  if (bitmap.close) bitmap.close();
  return { analysis, cover };
}

/** Small JPEG (max 400px) as a data URL, used to carry the photo through the cloud. */
export async function makeThumb(blob, maxSide = 400, quality = 0.7) {
  const bitmap = await loadBitmap(blob);
  const canvas = toCanvas(bitmap, maxSide);
  if (bitmap.close) bitmap.close();
  return canvas.toDataURL('image/jpeg', quality);
}
