// Invoice PDFs can't embed WebP, but browsers read it fine — so a WebP logo is
// converted to PNG (keeping its transparency) in the browser before upload.
export function isWebp(file: File): boolean {
  return file.type === "image/webp";
}

export async function webpToPng(file: File): Promise<File> {
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement("canvas");
  canvas.width = bitmap.width;
  canvas.height = bitmap.height;
  const ctx = canvas.getContext("2d");
  if (!ctx) throw new Error("Couldn't convert that image.");
  ctx.drawImage(bitmap, 0, 0);
  bitmap.close();
  const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/png"));
  if (!blob) throw new Error("Couldn't convert that image.");
  return new File([blob], file.name.replace(/\.webp$/i, "") + ".png", { type: "image/png" });
}
