// OCR with tesseract.js running in a Web Worker, fully on the device.
// The engine, WebAssembly core and language data are vendored under
// /vendor/tesseract so the feature keeps working offline.

let workerPromise = null;
let workerLangs = null;

const base = new URL('../../vendor/tesseract/', import.meta.url).href;

async function getWorker(langs, onProgress) {
  if (workerPromise && workerLangs === langs) return workerPromise;
  if (workerPromise) {
    try { (await workerPromise).terminate(); } catch { /* ignore */ }
  }
  workerLangs = langs;
  workerPromise = (async () => {
    const mod = await import('../../vendor/tesseract/tesseract.esm.min.js');
    const Tesseract = mod.default && mod.default.createWorker ? mod.default : mod;
    const worker = await Tesseract.createWorker(langs.split('+'), 1, {
      workerPath: `${base}worker.min.js`,
      corePath: base.replace(/\/$/, ''),
      langPath: `${base}lang`,
      gzip: true,
      workerBlobURL: false,
      logger: (m) => onProgress && onProgress(m),
    });
    await worker.setParameters({ preserve_interword_spaces: '1' });
    return worker;
  })();
  return workerPromise;
}

/**
 * Recognises text lines in the canvas.
 * @returns {Promise<string[]>} non-empty lines ordered top to bottom
 */
export async function recognizeText(canvas, { langs = 'por+eng', onProgress } = {}) {
  const worker = await getWorker(langs, onProgress);
  const { data } = await worker.recognize(canvas);
  const lines = (data.lines || []).map((l) => l.text.replace(/\s+/g, ' ').trim()).filter((l) => l.length >= 2);
  if (lines.length) return lines;
  return (data.text || '').split('\n').map((l) => l.trim()).filter((l) => l.length >= 2);
}

export async function disposeOcr() {
  if (!workerPromise) return;
  try { (await workerPromise).terminate(); } catch { /* ignore */ }
  workerPromise = null;
  workerLangs = null;
}
