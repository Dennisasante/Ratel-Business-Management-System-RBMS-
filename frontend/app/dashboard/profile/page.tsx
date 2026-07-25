"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import { Building2, Upload, Pencil } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessUpdatePayload } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Modal from "@/components/Modal";
import BusinessProfileForm from "@/components/BusinessProfileForm";

export default function ProfilePage() {
  const { session, business, loading, refreshBusiness } = useAuth();
  const router = useRouter();
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [showEdit, setShowEdit] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const canEdit = session?.role === "OWNER" || session?.role === "MANAGER";

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  async function handleLogoSelected(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file || !session) return;
    setUploadError(null);
    setUploading(true);
    try {
      await api.uploadBusinessLogo(session.token, file);
      await refreshBusiness();
    } catch (err) {
      setUploadError(err instanceof ApiError ? err.message : "Couldn't upload that image.");
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  }

  async function handleUpdateProfile(payload: BusinessUpdatePayload) {
    if (!session) return;
    await api.updateBusinessProfile(session.token, payload);
    await refreshBusiness();
    setShowEdit(false);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Business Profile" subtitle="How your business shows up across Ratel — logo, contact details, and industry." />

      <Card className="max-w-2xl p-5">
        <div className="flex items-center justify-between">
          <h2 className="text-base font-semibold text-ink-900">Business details</h2>
          {canEdit && (
            <button
              onClick={() => setShowEdit(true)}
              className="flex items-center gap-1 text-xs font-medium text-accent-hover hover:underline"
            >
              <Pencil size={12} />
              Edit
            </button>
          )}
        </div>

        <div className="mt-4 flex items-center gap-4">
          {business?.logoUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={business.logoUrl} alt="" className="h-16 w-16 rounded-lg object-cover" />
          ) : (
            <div className="flex h-16 w-16 items-center justify-center rounded-lg bg-canvas text-ink-300">
              <Building2 size={24} />
            </div>
          )}
          {canEdit && (
            <div>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,image/webp"
                onChange={handleLogoSelected}
                className="hidden"
                id="logo-upload"
              />
              <label
                htmlFor="logo-upload"
                className="inline-flex cursor-pointer items-center gap-1.5 rounded-lg border border-border px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-canvas"
              >
                <Upload size={13} />
                {uploading ? "Uploading..." : business?.logoUrl ? "Change logo" : "Upload logo"}
              </label>
              <p className="mt-1 text-xs text-ink-500">PNG, JPEG, or WEBP, up to 3MB</p>
              {uploadError && <p className="mt-1 text-xs text-danger">{uploadError}</p>}
            </div>
          )}
        </div>

        <dl className="mt-4 space-y-3 text-sm">
          <div className="flex justify-between border-b border-border pb-3">
            <dt className="text-ink-500">Industry</dt>
            <dd className="font-medium text-ink-900">{business?.industry ?? "—"}</dd>
          </div>
          <div className="flex justify-between border-b border-border pb-3">
            <dt className="text-ink-500">Location</dt>
            <dd className="font-medium text-ink-900">{business?.location ?? "—"}</dd>
          </div>
          <div className="flex justify-between border-b border-border pb-3">
            <dt className="text-ink-500">Contact email</dt>
            <dd className="font-medium text-ink-900">{business?.contactEmail ?? "—"}</dd>
          </div>
          <div className="flex justify-between border-b border-border pb-3">
            <dt className="text-ink-500">Contact phone</dt>
            <dd className="font-medium text-ink-900">{business?.contactPhone ?? "—"}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-ink-500">Currency</dt>
            <dd className="font-medium text-ink-900">{business?.currency}</dd>
          </div>
        </dl>

        <div className="mt-4">
          <p className="text-sm text-ink-500">Enabled modules</p>
          <div className="mt-2 flex flex-wrap gap-2">
            {business?.enabledModules.map((m) => (
              <Badge key={m} tone="accent">
                {m}
              </Badge>
            ))}
          </div>
        </div>
      </Card>

      {showEdit && business && (
        <Modal title="Edit business details" onClose={() => setShowEdit(false)}>
          <BusinessProfileForm initial={business} onSubmit={handleUpdateProfile} />
        </Modal>
      )}
    </div>
  );
}
