"use client";

import { useEffect, useState, useCallback, useRef } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, ExternalLink, RefreshCw } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ServiceCatalogItem,
  ServiceCatalogItemPayload,
  ServicePackage,
  ServicePackagePayload,
  ServiceType,
  ServiceTypePayload,
} from "@/lib/api";
import ServiceCatalogManager from "@/components/ServiceCatalogManager";
import ServiceTypeManager from "@/components/ServiceTypeManager";
import ServicePackageManager from "@/components/ServicePackageManager";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";

export default function ServiceCatalogPage() {
  const { session, business, loading } = useAuth();
  const router = useRouter();

  const [serviceTypes, setServiceTypes] = useState<ServiceType[]>([]);
  const [catalog, setCatalog] = useState<ServiceCatalogItem[]>([]);
  const [packages, setPackages] = useState<ServicePackage[]>([]);
  const [fetching, setFetching] = useState(true);
  const [previewNonce, setPreviewNonce] = useState(0);

  const iframeRef = useRef<HTMLIFrameElement>(null);
  const origin = typeof window !== "undefined" ? window.location.origin : "";
  const previewUrl = business ? `${origin}/book/${business.slug}` : "";

  const loadAll = useCallback(async () => {
    if (!session) return;
    const [types, cat, pkgs] = await Promise.all([
      api.listServiceTypes(session.token),
      api.listServiceCatalog(session.token),
      api.listServicePackages(session.token),
    ]);
    setServiceTypes(types);
    setCatalog(cat);
    setPackages(pkgs);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadAll().finally(() => setFetching(false));
  }, [session, loadAll]);

  async function handleCreateCatalogItem(payload: ServiceCatalogItemPayload) {
    if (!session) return;
    const item = await api.createServiceCatalogItem(session.token, payload);
    setCatalog((prev) => [...prev, item].sort((a, b) => a.name.localeCompare(b.name)));
  }

  async function handleEditCatalogItem(id: string, fields: { serviceTypeId: string; name: string; price: number }) {
    if (!session) return;
    const existing = catalog.find((c) => c.id === id);
    if (!existing) return;
    const item = await api.updateServiceCatalogItem(session.token, id, {
      ...fields,
      bookableOnline: existing.bookableOnline,
      durationMinutes: existing.durationMinutes,
      maxConcurrentBookings: existing.maxConcurrentBookings,
      requiresLocation: existing.requiresLocation,
      paymentPolicyOverride: existing.paymentPolicyOverride ?? "",
    });
    setCatalog((prev) => prev.map((c) => (c.id === id ? item : c)).sort((a, b) => a.name.localeCompare(b.name)));
  }

  async function handleToggleCatalogItem(id: string, active: boolean) {
    if (!session) return;
    const item = await api.setServiceCatalogItemActive(session.token, id, active);
    setCatalog((prev) => prev.map((c) => (c.id === id ? item : c)));
  }

  async function handleUpdateCatalogBookingSettings(
    id: string,
    settings: {
      bookableOnline: boolean;
      durationMinutes: number;
      maxConcurrentBookings: number;
      requiresLocation: boolean;
      paymentPolicyOverride: "NONE" | "DEPOSIT" | "FULL" | "";
    }
  ) {
    if (!session) return;
    const existing = catalog.find((c) => c.id === id);
    if (!existing) return;
    const item = await api.updateServiceCatalogItem(session.token, id, {
      serviceTypeId: existing.serviceTypeId,
      name: existing.name,
      price: existing.price,
      ...settings,
    });
    setCatalog((prev) => prev.map((c) => (c.id === id ? item : c)));
  }

  async function handleCreatePackage(payload: ServicePackagePayload) {
    if (!session) return;
    const pkg = await api.createServicePackage(session.token, payload);
    setPackages((prev) => [...prev, pkg].sort((a, b) => a.name.localeCompare(b.name)));
  }

  async function handleUpdatePackage(id: string, payload: ServicePackagePayload) {
    if (!session) return;
    const pkg = await api.updateServicePackage(session.token, id, payload);
    setPackages((prev) => prev.map((p) => (p.id === id ? pkg : p)));
  }

  async function handleTogglePackageActive(id: string, active: boolean) {
    if (!session) return;
    const pkg = await api.setServicePackageActive(session.token, id, active);
    setPackages((prev) => prev.map((p) => (p.id === id ? pkg : p)));
  }

  async function handleCreateServiceType(payload: ServiceTypePayload) {
    if (!session) return;
    await api.createServiceType(session.token, payload);
    await loadAll();
  }

  async function handleRenameServiceType(id: string, payload: ServiceTypePayload) {
    if (!session) return;
    await api.renameServiceType(session.token, id, payload);
    await loadAll();
  }

  async function handleDeleteServiceType(id: string) {
    if (!session) return;
    await api.deleteServiceType(session.token, id);
    await loadAll();
  }

  function refreshPreview() {
    setPreviewNonce((n) => n + 1);
    if (iframeRef.current) iframeRef.current.src = `${previewUrl}?_preview=${Date.now()}`;
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Services"
        subtitle="Categories, prices, and service groups — plus a live preview of what customers see."
        actions={
          <Link href="/dashboard/service-orders">
            <Button variant="secondary">
              <ArrowLeft size={16} /> Back to Service Orders
            </Button>
          </Link>
        }
      />

      {business && (
        <Card className="p-5">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 className="text-base font-semibold text-ink-900">Live preview</h2>
              <p className="mt-1 text-sm text-ink-500">What customers see at your booking page — refresh after making changes below.</p>
            </div>
            <div className="flex shrink-0 items-center gap-2">
              <Button variant="secondary" onClick={refreshPreview}>
                <RefreshCw size={15} /> Refresh
              </Button>
              <a href={previewUrl} target="_blank" rel="noopener noreferrer">
                <Button variant="secondary">
                  <ExternalLink size={15} /> Open in new tab
                </Button>
              </a>
            </div>
          </div>
          <div className="mt-4 hidden overflow-hidden rounded-xl border border-border lg:block">
            <iframe
              key={previewNonce}
              ref={iframeRef}
              src={previewUrl}
              title="Booking page preview"
              className="h-[600px] w-full"
            />
          </div>
        </Card>
      )}

      {fetching ? (
        <p className="text-sm text-ink-500">Loading...</p>
      ) : (
        <>
          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Categories</h2>
            <p className="mt-1 text-sm text-ink-500">The groups your services and service groups belong to.</p>
            <div className="mt-4">
              <ServiceTypeManager
                types={serviceTypes}
                onCreate={handleCreateServiceType}
                onRename={handleRenameServiceType}
                onDelete={handleDeleteServiceType}
              />
            </div>
          </Card>

          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Services</h2>
            <p className="mt-1 text-sm text-ink-500">Individual services and prices — used for both walk-in orders and online booking.</p>
            <div className="mt-4">
              <ServiceCatalogManager
                items={catalog}
                serviceTypes={serviceTypes}
                onCreate={handleCreateCatalogItem}
                onEdit={handleEditCatalogItem}
                onToggleActive={handleToggleCatalogItem}
                onUpdateBookingSettings={handleUpdateCatalogBookingSettings}
              />
            </div>
          </Card>

          <Card className="p-5">
            <h2 className="text-base font-semibold text-ink-900">Service Groups</h2>
            <p className="mt-1 text-sm text-ink-500">Bundles of services sold and booked as one appointment.</p>
            <div className="mt-4">
              <ServicePackageManager
                packages={packages}
                serviceTypes={serviceTypes}
                catalogItems={catalog.filter((c) => c.active)}
                onCreate={handleCreatePackage}
                onUpdate={handleUpdatePackage}
                onToggleActive={handleTogglePackageActive}
              />
            </div>
          </Card>
        </>
      )}
    </div>
  );
}
