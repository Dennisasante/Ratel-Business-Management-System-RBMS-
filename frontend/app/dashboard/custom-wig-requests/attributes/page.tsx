"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Plus, Pencil, Trash2, Sparkles } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, CustomItemAttribute, CustomItemAttributePayload } from "@/lib/api";
import Modal from "@/components/Modal";
import CustomWigAttributeForm from "@/components/CustomWigAttributeForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import CardSkeleton from "@/components/ui/CardSkeleton";

type ModalState = { type: "none" } | { type: "add" } | { type: "edit"; attribute: CustomItemAttribute };

export default function CustomWigAttributesPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [attributes, setAttributes] = useState<CustomItemAttribute[]>([]);
  const [fetching, setFetching] = useState(true);
  const [modal, setModal] = useState<ModalState>({ type: "none" });
  const [rowError, setRowError] = useState<{ id: string; message: string } | null>(null);
  const [rowBusy, setRowBusy] = useState<string | null>(null);

  const loadAttributes = useCallback(async () => {
    if (!session) return;
    const data = await api.listCustomWigAttributes(session.token);
    setAttributes(data);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!loading && session?.role === "STAFF") router.push("/dashboard");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    setFetching(true);
    loadAttributes().finally(() => setFetching(false));
  }, [session, loadAttributes]);

  async function handleCreate(payload: CustomItemAttributePayload) {
    if (!session) return;
    await api.createCustomWigAttribute(session.token, payload);
    await loadAttributes();
    setModal({ type: "none" });
  }

  async function handleUpdate(id: string, payload: CustomItemAttributePayload) {
    if (!session) return;
    await api.updateCustomWigAttribute(session.token, id, payload);
    await loadAttributes();
    setModal({ type: "none" });
  }

  async function handleDelete(attribute: CustomItemAttribute) {
    if (!session) return;
    setRowBusy(attribute.id);
    setRowError(null);
    try {
      await api.deleteCustomWigAttribute(session.token, attribute.id);
      await loadAttributes();
    } catch (err) {
      setRowError({ id: attribute.id, message: err instanceof ApiError ? err.message : "Couldn't remove this attribute." });
    } finally {
      setRowBusy(null);
    }
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <div>
        <Link
          href="/dashboard/custom-wig-requests"
          className="inline-flex items-center gap-1.5 text-sm font-medium text-ink-500 hover:text-ink-900"
        >
          <ArrowLeft size={14} /> Back to requests
        </Link>
      </div>

      <div className="flex items-center justify-between">
        <PageHeader
          title="Pricing Rules"
          subtitle="What customers can choose from in the custom wig configurator, and what each choice adds to the price."
        />
        <Button onClick={() => setModal({ type: "add" })}>
          <Plus size={15} className="mr-1.5" /> Add attribute
        </Button>
      </div>

      {fetching ? (
        <CardSkeleton count={2} />
      ) : attributes.length === 0 ? (
        <Card>
          <EmptyState
            icon={Sparkles}
            title="No pricing rules yet"
            description='Add an attribute like "Length" or "Texture" with priced options to start taking custom requests.'
            action={
              <Button onClick={() => setModal({ type: "add" })}>
                <Plus size={15} className="mr-1.5" /> Add attribute
              </Button>
            }
          />
        </Card>
      ) : (
        <div className="flex flex-col gap-4">
          {attributes.map((attr) => (
            <Card key={attr.id} className="p-5">
              <div className="flex items-center justify-between">
                <h3 className="text-base font-semibold text-ink-900">{attr.name}</h3>
                <div className="flex items-center gap-2">
                  <button
                    onClick={() => setModal({ type: "edit", attribute: attr })}
                    className="rounded-md p-1.5 text-ink-500 hover:bg-canvas hover:text-ink-900"
                    aria-label="Edit"
                  >
                    <Pencil size={15} />
                  </button>
                  <button
                    onClick={() => handleDelete(attr)}
                    disabled={rowBusy === attr.id}
                    className="rounded-md p-1.5 text-danger hover:bg-danger-soft disabled:cursor-not-allowed disabled:opacity-50"
                    aria-label="Remove"
                  >
                    <Trash2 size={15} />
                  </button>
                </div>
              </div>
              <div className="mt-3 flex flex-wrap gap-2">
                {attr.options.map((opt) => (
                  <span
                    key={opt.id}
                    className="rounded-full border border-border bg-canvas px-2.5 py-1 text-xs text-ink-700"
                  >
                    {opt.label} <span className="text-ink-500">+{opt.priceModifier.toFixed(2)}</span>
                  </span>
                ))}
              </div>
              {rowError?.id === attr.id && <p className="mt-2 text-xs text-danger">{rowError.message}</p>}
            </Card>
          ))}
        </div>
      )}

      {modal.type === "add" && (
        <Modal title="Add attribute" onClose={() => setModal({ type: "none" })}>
          <CustomWigAttributeForm submitLabel="Add attribute" onSubmit={handleCreate} />
        </Modal>
      )}

      {modal.type === "edit" && (
        <Modal title="Edit attribute" onClose={() => setModal({ type: "none" })}>
          <CustomWigAttributeForm
            initial={modal.attribute}
            submitLabel="Save changes"
            onSubmit={(payload) => handleUpdate(modal.attribute.id, payload)}
          />
        </Modal>
      )}
    </div>
  );
}
