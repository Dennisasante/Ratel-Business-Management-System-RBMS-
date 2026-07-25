"use client";

import { useEffect, useRef, useState } from "react";
import { Trash2, Upload } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, ServiceOrderPhoto } from "@/lib/api";

export default function ServiceOrderPhotoGallery({ serviceOrderId }: { serviceOrderId: string }) {
  const { session } = useAuth();
  const [photos, setPhotos] = useState<ServiceOrderPhoto[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!session) return;
    api.listServiceOrderPhotos(session.token, serviceOrderId)
      .then(setPhotos)
      .finally(() => setLoading(false));
  }, [session, serviceOrderId]);

  async function handleUpload(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !session) return;
    setError(null);
    setUploading(true);
    try {
      const photo = await api.uploadServiceOrderPhoto(session.token, serviceOrderId, file);
      setPhotos((prev) => [photo, ...prev]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't upload that photo.");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleDelete(photoId: string) {
    if (!session) return;
    setDeletingId(photoId);
    try {
      await api.deleteServiceOrderPhoto(session.token, serviceOrderId, photoId);
      setPhotos((prev) => prev.filter((p) => p.id !== photoId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't remove that photo.");
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <div className="flex flex-col gap-3 border-t border-border pt-4">
      <div className="flex items-center justify-between">
        <label className="text-sm font-medium text-ink-700">Photos</label>
        <div>
          <input ref={fileInputRef} type="file" accept="image/png,image/jpeg,image/webp" onChange={handleUpload} className="hidden" id="service-order-photo-upload" />
          <label
            htmlFor="service-order-photo-upload"
            className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-canvas"
          >
            <Upload size={13} />
            {uploading ? "Uploading..." : "Add photo"}
          </label>
        </div>
      </div>

      {error && <p className="text-xs text-danger">{error}</p>}

      {loading ? (
        <p className="text-xs text-ink-500">Loading photos...</p>
      ) : photos.length === 0 ? (
        <p className="text-xs text-ink-500">No photos yet — add before/after shots or a client's reference image.</p>
      ) : (
        <div className="grid grid-cols-3 gap-2">
          {photos.map((photo) => (
            <div key={photo.id} className="group relative aspect-square overflow-hidden rounded-lg border border-border">
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={photo.url} alt="" className="h-full w-full object-cover" />
              <button
                onClick={() => handleDelete(photo.id)}
                disabled={deletingId === photo.id}
                aria-label="Delete photo"
                className="absolute right-1 top-1 rounded-md bg-black/60 p-1 text-white opacity-0 transition group-hover:opacity-100 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Trash2 size={12} />
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
