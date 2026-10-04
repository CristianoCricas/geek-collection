# Third-party libraries bundled with the PWA

| Library | Version | License | Files |
| --- | --- | --- | --- |
| [tesseract.js](https://github.com/naptha/tesseract.js) | 5.1.1 | Apache-2.0 | `tesseract/tesseract.esm.min.js`, `tesseract/worker.min.js` |
| [tesseract.js-core](https://github.com/naptha/tesseract.js-core) | 5.1.1 | Apache-2.0 | `tesseract/tesseract-core-simd-lstm.wasm.js` (WebAssembly SIMD build, LSTM only) |
| [@tesseract.js-data](https://github.com/naptha/tessdata) | 4.0.0_best_int | Apache-2.0 | `tesseract/lang/por.traineddata.gz`, `tesseract/lang/eng.traineddata.gz` |
| [@zxing/library](https://github.com/zxing-js/library) | 0.21.3 | Apache-2.0 | `zxing/zxing.min.js` (UMD bundle) |

They are vendored (instead of loaded from a CDN) so the app works offline and
does not depend on third-party hosts at runtime.
