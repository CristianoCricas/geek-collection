// Barcode reading: native BarcodeDetector when available (Chrome on Android),
// otherwise the vendored ZXing library. Both run entirely on the device.

const FORMATS = ['ean_13', 'ean_8', 'upc_a', 'upc_e', 'code_128', 'qr_code'];
let zxingPromise = null;

function loadZxing() {
  if (window.ZXing) return Promise.resolve(window.ZXing);
  zxingPromise ||= new Promise((resolve, reject) => {
    const s = document.createElement('script');
    s.src = new URL('../../vendor/zxing/zxing.min.js', import.meta.url).href;
    s.onload = () => resolve(window.ZXing);
    s.onerror = () => reject(new Error('Falha ao carregar o leitor de código de barras'));
    document.head.appendChild(s);
  });
  return zxingPromise;
}

async function nativeDetect(canvas) {
  if (!('BarcodeDetector' in window)) return null;
  try {
    const supported = await window.BarcodeDetector.getSupportedFormats();
    const formats = FORMATS.filter((f) => supported.includes(f));
    if (!formats.length) return null;
    const detector = new window.BarcodeDetector({ formats });
    const codes = await detector.detect(canvas);
    return codes.map((c) => c.rawValue).filter(Boolean);
  } catch {
    return null;
  }
}

function zxingDecode(ZXing, canvas) {
  const ctx = canvas.getContext('2d', { willReadFrequently: true });
  const { width, height } = canvas;
  const { data } = ctx.getImageData(0, 0, width, height);
  const lum = new Uint8ClampedArray(width * height);
  for (let i = 0, j = 0; i < data.length; i += 4, j++) {
    lum[j] = (data[i] * 299 + data[i + 1] * 587 + data[i + 2] * 114) / 1000;
  }
  const source = new ZXing.RGBLuminanceSource(lum, width, height);
  const hints = new Map();
  hints.set(ZXing.DecodeHintType.TRY_HARDER, true);
  hints.set(ZXing.DecodeHintType.POSSIBLE_FORMATS, [
    ZXing.BarcodeFormat.EAN_13, ZXing.BarcodeFormat.EAN_8, ZXing.BarcodeFormat.UPC_A,
    ZXing.BarcodeFormat.UPC_E, ZXing.BarcodeFormat.CODE_128, ZXing.BarcodeFormat.QR_CODE,
  ]);
  const reader = new ZXing.MultiFormatReader();
  reader.setHints(hints);
  const attempts = [
    () => new ZXing.BinaryBitmap(new ZXing.HybridBinarizer(source)),
    () => new ZXing.BinaryBitmap(new ZXing.GlobalHistogramBinarizer(source)),
    () => new ZXing.BinaryBitmap(new ZXing.HybridBinarizer(source.rotateCounterClockwise())),
  ];
  for (const make of attempts) {
    try {
      const result = reader.decode(make());
      if (result && result.getText()) return [result.getText()];
    } catch { /* NotFoundException: try the next strategy */ }
  }
  return [];
}

/** Returns raw barcode strings found in the canvas (possibly empty). */
export async function readBarcodes(canvas) {
  const native = await nativeDetect(canvas);
  if (native && native.length) return [...new Set(native)];
  try {
    const ZXing = await loadZxing();
    const scales = [1, 0.6];
    for (const scale of scales) {
      let target = canvas;
      if (scale !== 1) {
        target = document.createElement('canvas');
        target.width = Math.round(canvas.width * scale);
        target.height = Math.round(canvas.height * scale);
        target.getContext('2d').drawImage(canvas, 0, 0, target.width, target.height);
      }
      const found = zxingDecode(ZXing, target);
      if (found.length) return found;
    }
  } catch (e) {
    console.warn('ZXing indisponível', e);
  }
  return native || [];
}
