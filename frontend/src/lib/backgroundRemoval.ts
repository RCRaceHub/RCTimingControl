/**
 * Removes the background of a headshot or car photo in the browser (#695). Nothing leaves the machine: the model
 * (`public/models/u2netp.onnx`, U²-Net-P, Apache-2.0, 4.6 MB) and the WebAssembly runtime are served by the app
 * itself, so it works at the track with no internet. Both are fetched only when someone first uses it.
 *
 * The image maths ({@link toModelInput}, {@link normaliseMask}, {@link applyMask}) is pure and tested; only
 * {@link removeBackground} touches the model and the canvas.
 */

/** The side of the square the model reads and writes. */
export const MODEL_SIZE = 320;
/** The upload limit of the server; a cut-out over this is shrunk until it fits. */
export const MAX_UPLOAD_BYTES = 2 * 1024 * 1024;
const MEAN = [0.485, 0.456, 0.406];
const STD = [0.229, 0.224, 0.225];
/** Longest side tried for the cut-out, largest first. */
const SIDES = [1600, 1200, 900, 640];

/** Turns RGBA pixels at {@link MODEL_SIZE} into the model's planar, normalised float input (1 x 3 x 320 x 320). */
export function toModelInput(rgba: Uint8ClampedArray): Float32Array {
  const plane = MODEL_SIZE * MODEL_SIZE;
  const out = new Float32Array(3 * plane);
  for (let i = 0; i < plane; i++) {
    for (let c = 0; c < 3; c++) {
      out[c * plane + i] = (rgba[i * 4 + c] / 255 - MEAN[c]) / STD[c];
    }
  }
  return out;
}

/** Stretches the model's raw output to 0..255 so the weakest pixel is clear and the strongest solid. */
export function normaliseMask(raw: ArrayLike<number>): Uint8ClampedArray {
  let min = Infinity;
  let max = -Infinity;
  for (let i = 0; i < raw.length; i++) {
    min = Math.min(min, raw[i]);
    max = Math.max(max, raw[i]);
  }
  const range = max - min || 1;
  const out = new Uint8ClampedArray(raw.length);
  for (let i = 0; i < raw.length; i++) out[i] = Math.round(((raw[i] - min) / range) * 255);
  return out;
}

/** Writes the mask, scaled up smoothly to the picture's size, into the alpha of `rgba` (changed in place). */
export function applyMask(rgba: Uint8ClampedArray, width: number, height: number, mask: Uint8ClampedArray, maskSize: number): void {
  for (let y = 0; y < height; y++) {
    const fy = Math.min(maskSize - 1, Math.max(0, ((y + 0.5) * maskSize) / height - 0.5));
    const y0 = Math.floor(fy);
    const y1 = Math.min(maskSize - 1, y0 + 1);
    const ty = fy - y0;
    for (let x = 0; x < width; x++) {
      const fx = Math.min(maskSize - 1, Math.max(0, ((x + 0.5) * maskSize) / width - 0.5));
      const x0 = Math.floor(fx);
      const x1 = Math.min(maskSize - 1, x0 + 1);
      const tx = fx - x0;
      const top = mask[y0 * maskSize + x0] * (1 - tx) + mask[y0 * maskSize + x1] * tx;
      const bottom = mask[y1 * maskSize + x0] * (1 - tx) + mask[y1 * maskSize + x1] * tx;
      const alpha = top * (1 - ty) + bottom * ty;
      const p = (y * width + x) * 4 + 3;
      rgba[p] = Math.round((rgba[p] * alpha) / 255);
    }
  }
}

/** Fits a width and height inside a longest side without scaling up. */
export function fitWithin(width: number, height: number, longest: number): { width: number; height: number } {
  const scale = Math.min(1, longest / Math.max(width, height));
  return { width: Math.max(1, Math.round(width * scale)), height: Math.max(1, Math.round(height * scale)) };
}

type Ort = typeof import('onnxruntime-web/wasm');
let session: Promise<{ ort: Ort; session: import('onnxruntime-web/wasm').InferenceSession }> | null = null;

function loadSession() {
  session ??= (async () => {
    const [ort, wasm] = await Promise.all([
      import('onnxruntime-web/wasm'),
      import('../../node_modules/onnxruntime-web/dist/ort-wasm-simd-threaded.wasm?url'),
    ]);
    // One thread: several need a cross-origin isolated page, which a plain http venue server is not
    ort.env.wasm.numThreads = 1;
    ort.env.wasm.proxy = false;
    ort.env.wasm.wasmPaths = { wasm: wasm.default } as never;
    const response = await fetch(`${import.meta.env.BASE_URL}models/u2netp.onnx`);
    if (!response.ok) throw new Error('The background removal model could not be loaded');
    const created = await ort.InferenceSession.create(new Uint8Array(await response.arrayBuffer()), {
      executionProviders: ['wasm'],
    });
    return { ort, session: created };
  })();
  // A failed load is tried again next time
  session.catch(() => { session = null; });
  return session;
}

function draw(bitmap: ImageBitmap, width: number, height: number): ImageData {
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d', { willReadFrequently: true })!;
  ctx.drawImage(bitmap, 0, 0, width, height);
  return ctx.getImageData(0, 0, width, height);
}

function toPng(image: ImageData): Promise<Blob> {
  const canvas = document.createElement('canvas');
  canvas.width = image.width;
  canvas.height = image.height;
  canvas.getContext('2d')!.putImageData(image, 0, 0);
  return new Promise((resolve, reject) =>
    canvas.toBlob(blob => (blob ? resolve(blob) : reject(new Error('The picture could not be saved as PNG'))), 'image/png'));
}

/**
 * Cuts the subject out of a PNG or JPEG and returns a PNG with a transparent background, shrunk if needed to fit the
 * server's 2 MB limit.
 *
 * @throws Error if the model cannot be loaded or run, or the file is not an image the browser can read
 */
export async function removeBackground(file: Blob): Promise<Blob> {
  const { ort, session: model } = await loadSession();
  const bitmap = await createImageBitmap(file);
  try {
    const small = draw(bitmap, MODEL_SIZE, MODEL_SIZE);
    const input = new ort.Tensor('float32', toModelInput(small.data), [1, 3, MODEL_SIZE, MODEL_SIZE]);
    const result = await model.run({ [model.inputNames[0]]: input });
    const mask = normaliseMask(result[model.outputNames[0]].data as Float32Array);
    let blob: Blob | null = null;
    for (const side of SIDES) {
      const { width, height } = fitWithin(bitmap.width, bitmap.height, side);
      const full = draw(bitmap, width, height);
      applyMask(full.data, width, height, mask, MODEL_SIZE);
      blob = await toPng(full);
      if (blob.size <= MAX_UPLOAD_BYTES) return blob;
    }
    throw new Error('The cut-out is over 2 MB even when shrunk; use the original');
  } finally {
    bitmap.close();
  }
}
