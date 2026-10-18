import { AppError } from "../../lib/app-error.js";

/** Accept only bounded baseline JPEG previews; remove metadata before storage. */
export function memoryImage(base64: string): Buffer {
  const image = Buffer.from(base64, "base64");
  const invalid = () => new AppError("invalid_photo", 400);
  if (image.length < 20 || image.length > 70000 || image[0] !== 0xff || image[1] !== 0xd8 || image[image.length - 2] !== 0xff || image[image.length - 1] !== 0xd9) throw invalid();
  const parts: Buffer[] = [image.subarray(0, 2)];
  let offset = 2;
  let dimensions = false;
  while (offset + 4 <= image.length) {
    const start = offset;
    if (image[offset++] !== 0xff) throw invalid();
    while (image[offset] === 0xff) offset++;
    const marker = image[offset++];
    if (marker === undefined || offset + 2 > image.length) throw invalid();
    const length = image.readUInt16BE(offset);
    if (length < 2 || offset + length > image.length) throw invalid();
    if (marker === 0xc0) {
      if (length < 11 || image[offset + 2] !== 8) throw invalid();
      const height = image.readUInt16BE(offset + 3);
      const width = image.readUInt16BE(offset + 5);
      if (width < 1 || height < 1 || width > 1280 || height > 1280) throw invalid();
      dimensions = true;
    } else if ([0xc1,0xc2,0xc3,0xc5,0xc6,0xc7,0xc9,0xca,0xcb,0xcd,0xce,0xcf].includes(marker)) throw invalid();
    if (marker === 0xda) {
      if (!dimensions || length < 6) throw invalid();
      parts.push(image.subarray(start));
      return Buffer.concat(parts);
    }
    // EXIF, comments and application metadata never leave the upload boundary.
    if (!(marker >= 0xe0 && marker <= 0xef) && marker !== 0xfe) parts.push(image.subarray(start, offset + length));
    offset += length;
  }
  throw invalid();
}
