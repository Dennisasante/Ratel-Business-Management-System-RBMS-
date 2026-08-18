"use client";

import { useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { useEffect } from "react";
import Link from "next/link";
import { Building2, Upload, Pencil, Plug, Link2, Copy, Check } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, BusinessUpdatePayload } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/Modal";
import BusinessProfileForm from "@/components/BusinessProfileForm";

export default function ProfilePage() {
  const { session, business, loading, refreshBusiness } = useAuth();
  const router = useRouter();
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [showEdit, setShowEdit] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [editingSlug, setEditingSlug] = useState(false);
  const [slugInput, setSlugInput] = useState("");
  const [savingSlug, setSavingSlug] = useState(false);
  const [slugError, setSlugError] = useState<string | null>(null);
  const [copiedField, setCopiedField] = useState<string | null>(null);

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

  const origin = typeof window !== "undefined" ? window.location.origin : "";
  const startPageUrl = business ? `${origin}/start/${business.slug}` : "";
  const bookingPageUrl = business ? `${origin}/book/${business.slug}` : "";
  const orderPageUrl = business ? `${origin}/order/${business.slug}` : "";
  const whatsappGreeting = business
    ? `Hi! Thanks for reaching out to ${business.name} 👋 Tap here to book or place a custom order: ${startPageUrl}`
    : "";

  function startEditSlug() {
    setSlugInput(business?.slug ?? "");
    setSlugError(null);
    setEditingSlug(true);
  }

  async function saveSlug(e: React.FormEvent) {
    e.preventDefault();
    if (!session) return;
    setSavingSlug(true);
    setSlugError(null);
    try {
      await api.updateBusinessSlug(session.token, slugInput);
      await refreshBusiness();
      setEditingSlug(false);
    } catch (err) {
      setSlugError(err instanceof ApiError ? err.message : "Couldn't save that link.");
    } finally {
      setSavingSlug(false);
    }
  }

  async function copyToClipboard(text: string, field: string) {
    await navigator.clipboard.writeText(text);
    setCopiedField(field);
    setTimeout(() => setCopiedField(null), 2000);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="Business Profile" subtitle="How your business shows up across Tallia — logo, contact details, and industry." />

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

      <Card className="max-w-2xl p-5">
        <div className="flex items-center gap-2">
          <Link2 size={16} className="text-ink-500" />
          <h2 className="text-base font-semibold text-ink-900">Your customer link</h2>
        </div>
        <p className="mt-1 text-xs text-ink-500">
          One link for everything — booking, custom orders, and whatever ships next. Put this in your WhatsApp bio or
          pinned chat so customers can reach you even without a website.
        </p>

        {!editingSlug ? (
          <>
            <div className="mt-3 flex items-center gap-2">
              <code className="flex-1 truncate rounded-lg bg-accent-soft px-3 py-2 text-sm font-medium text-accent-hover">{startPageUrl}</code>
              <button
                onClick={() => copyToClipboard(startPageUrl, "start")}
                className="flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-xs font-medium text-ink-700 hover:bg-canvas"
              >
                {copiedField === "start" ? <Check size={13} /> : <Copy size={13} />}
                {copiedField === "start" ? "Copied" : "Copy"}
              </button>
              {canEdit && (
                <button onClick={startEditSlug} className="text-xs font-medium text-accent-hover hover:underline">
                  Edit
                </button>
              )}
            </div>

            <details className="mt-3">
              <summary className="cursor-pointer text-xs font-medium text-ink-500 hover:text-ink-700">
                Direct links (for a website you already have)
              </summary>
              <div className="mt-2 flex flex-col gap-2">
                <div className="flex items-center gap-2">
                  <code className="flex-1 truncate rounded-lg bg-canvas px-3 py-2 text-xs text-ink-700">{bookingPageUrl}</code>
                  <button
                    onClick={() => copyToClipboard(bookingPageUrl, "book")}
                    className="shrink-0 rounded-lg border border-border p-2 text-ink-700 hover:bg-canvas"
                    aria-label="Copy booking link"
                  >
                    {copiedField === "book" ? <Check size={13} /> : <Copy size={13} />}
                  </button>
                </div>
                <div className="flex items-center gap-2">
                  <code className="flex-1 truncate rounded-lg bg-canvas px-3 py-2 text-xs text-ink-700">{orderPageUrl}</code>
                  <button
                    onClick={() => copyToClipboard(orderPageUrl, "order")}
                    className="shrink-0 rounded-lg border border-border p-2 text-ink-700 hover:bg-canvas"
                    aria-label="Copy custom order link"
                  >
                    {copiedField === "order" ? <Check size={13} /> : <Copy size={13} />}
                  </button>
                </div>
              </div>
            </details>

            <div className="mt-4 border-t border-border pt-4">
              <p className="text-xs font-medium text-ink-700">Suggested WhatsApp greeting</p>
              <p className="mt-1 rounded-lg bg-canvas px-3 py-2 text-xs text-ink-700">{whatsappGreeting}</p>
              <button
                onClick={() => copyToClipboard(whatsappGreeting, "greeting")}
                className="mt-2 flex items-center gap-1.5 rounded-lg border border-border px-3 py-2 text-xs font-medium text-ink-700 hover:bg-canvas"
              >
                {copiedField === "greeting" ? <Check size={13} /> : <Copy size={13} />}
                {copiedField === "greeting" ? "Copied" : "Copy message"}
              </button>
            </div>
          </>
        ) : (
          <form onSubmit={saveSlug} className="mt-3 flex flex-col gap-2">
            <div className="flex items-center gap-1.5">
              <span className="text-sm text-ink-500">{origin}/start/</span>
              <input
                type="text"
                value={slugInput}
                onChange={(e) => setSlugInput(e.target.value)}
                className="flex-1 rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
              />
            </div>
            <p className="text-xs text-ink-500">This changes the link for booking and custom orders too — they all share the same one.</p>
            {slugError && <p className="text-sm text-danger">{slugError}</p>}
            <div className="flex gap-2">
              <Button type="submit" disabled={savingSlug || !slugInput.trim()}>
                {savingSlug ? "Saving..." : "Save"}
              </Button>
              <Button type="button" variant="ghost" onClick={() => setEditingSlug(false)}>
                Cancel
              </Button>
            </div>
          </form>
        )}
      </Card>

      {session.role === "OWNER" && (
        <Link
          href="/dashboard/profile/integrations"
          className="flex max-w-2xl items-center justify-between rounded-xl border border-border bg-surface p-5 shadow-card transition hover:border-accent"
        >
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-accent-soft text-accent-hover">
              <Plug size={18} />
            </div>
            <div>
              <p className="text-sm font-semibold text-ink-900">Integrations</p>
              <p className="text-xs text-ink-500">Your own Paystack, WooCommerce, and WhatsApp setup</p>
            </div>
          </div>
        </Link>
      )}

      {showEdit && business && (
        <Modal title="Edit business details" onClose={() => setShowEdit(false)}>
          <BusinessProfileForm initial={business} onSubmit={handleUpdateProfile} />
        </Modal>
      )}
    </div>
  );
}
