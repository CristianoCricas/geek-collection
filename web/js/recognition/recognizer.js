// Combines barcode reading and OCR into one RecognitionResult.
import { readBarcodes } from './barcode.js';
import { recognizeText } from './ocr.js';

/**
 * @param {HTMLCanvasElement} canvas
 * @param {{langs?: string, onStatus?: (msg: string) => void}} opts
 * @returns {Promise<{barcodes: string[], textLines: string[]}>}
 */
export async function recognizeImage(canvas, { langs = 'por+eng', onStatus } = {}) {
  onStatus && onStatus('Procurando código de barras…');
  const barcodes = await readBarcodes(canvas).catch(() => []);
  onStatus && onStatus('Lendo o texto da imagem…');
  let textLines = [];
  try {
    textLines = await recognizeText(canvas, {
      langs,
      onProgress: (m) => {
        if (onStatus && m.status === 'recognizing text' && typeof m.progress === 'number') {
          onStatus(`Lendo o texto da imagem… ${Math.round(m.progress * 100)}%`);
        } else if (onStatus && /loading|initializ/i.test(m.status || '')) {
          onStatus('Carregando o motor de OCR…');
        }
      },
    });
  } catch (e) {
    console.warn('OCR falhou', e);
  }
  return { barcodes, textLines };
}
