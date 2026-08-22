"use client";

import { useState, useEffect } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { ArrowLeft, Download, Upload, CheckCircle2, AlertCircle } from "lucide-react";
import { useAuth } from "@/lib/auth";
import { api, ApiError, ImportRow } from "@/lib/api";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

type ReviewRow = { row: ImportRow; included: boolean };

export default function InventoryImportPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [step, setStep] = useState<"upload" | "review" | "done">("upload");
  const [file, setFile] = useState<File | null>(null);
  const [previewing, setPreviewing] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);

  const [reviewRows, setReviewRows] = useState<ReviewRow[]>([]);
  const [confirming, setConfirming] = useState(false);
  const [confirmError, setConfirmError] = useState<string | null>(null);

  const [result, setResult] = useState<{ importedCount: number; skippedCount: number; skipped: { rowNumber: number; name: string | null; reason: string }[] } | null>(null);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  async function handleDownloadTemplate() {
    if (!session) return;
    await api.downloadImportTemplate(session.token);
  }

  async function handlePreview(e: React.FormEvent) {
    e.preventDefault();
    if (!session || !file) return;
    setPreviewing(true);
    setPreviewError(null);
    try {
      const preview = await api.previewProductImport(session.token, file);
      setReviewRows(preview.rows.map((row) => ({ row, included: row.valid })));
      setStep("review");
    } catch (err) {
      setPreviewError(err instanceof ApiError ? err.message : "Couldn't read that file.");
    } finally {
      setPreviewing(false);
    }
  }

  function toggleRow(rowNumber: number) {
    setReviewRows((prev) => prev.map((r) => (r.row.rowNumber === rowNumber ? { ...r, included: !r.included } : r)));
  }

  async function handleConfirm() {
    if (!session) return;
    const rowsToImport = reviewRows.filter((r) => r.included).map((r) => r.row);
    if (rowsToImport.length === 0) return;
    setConfirming(true);
    setConfirmError(null);
    try {
      const res = await api.confirmProductImport(session.token, rowsToImport);
      setResult(res);
      setStep("done");
    } catch (err) {
      setConfirmError(err instanceof ApiError ? err.message : "Couldn't import these rows.");
    } finally {
      setConfirming(false);
    }
  }

  function startOver() {
    setStep("upload");
    setFile(null);
    setReviewRows([]);
    setResult(null);
    setPreviewError(null);
    setConfirmError(null);
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  const includedCount = reviewRows.filter((r) => r.included).length;

  return (
    <div className="flex flex-col gap-6">
      <Link href="/dashboard/inventory" className="flex items-center gap-1.5 text-sm text-ink-500 hover:underline">
        <ArrowLeft size={14} /> Back to Inventory
      </Link>

      <PageHeader title="Import Inventory" subtitle="Bulk-load your product catalog from a CSV or Excel file." />

      {step === "upload" && (
        <Card className="p-5">
          <form onSubmit={handlePreview} className="flex flex-col gap-4">
            <div className="rounded-lg border border-border bg-canvas p-4 text-sm text-ink-700">
              <p className="mb-2 font-medium text-ink-900">Before you upload</p>
              <p>
                Your file needs these exact column headers: <code className="text-xs">name, category, sku, costPrice, sellingPrice,
                quantity, lowStockThreshold, supplierName</code>. Only <code className="text-xs">name</code> is required — everything
                else can be left blank.
              </p>
              <button
                type="button"
                onClick={handleDownloadTemplate}
                className="mt-3 flex items-center gap-1.5 text-sm font-medium text-accent-hover hover:underline"
              >
                <Download size={14} /> Download a template
              </button>
            </div>

            <div className="flex flex-col gap-1.5">
              <label className="text-sm font-medium text-ink-700">CSV or Excel file</label>
              <input
                type="file"
                accept=".csv,.xlsx,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 file:mr-3 file:rounded-md file:border-0 file:bg-canvas file:px-3 file:py-1.5 file:text-sm file:font-medium"
              />
            </div>

            {previewError && <p className="text-sm text-danger">{previewError}</p>}

            <Button type="submit" disabled={!file || previewing} className="w-full">
              <Upload size={16} className="mr-1.5" />
              {previewing ? "Reading file..." : "Preview"}
            </Button>
          </form>
        </Card>
      )}

      {step === "review" && (
        <>
          <div className="flex flex-wrap items-center gap-2">
            <Badge tone="success">{reviewRows.filter((r) => r.row.valid).length} valid</Badge>
            <Badge tone="danger">{reviewRows.filter((r) => !r.row.valid).length} need attention</Badge>
            <Badge tone="accent">{includedCount} selected to import</Badge>
          </div>

          <Card>
            <div className="max-h-[28rem] overflow-y-auto">
              <Table>
                <THead>
                  <Tr>
                    <Th></Th>
                    <Th>Row</Th>
                    <Th>Name</Th>
                    <Th>Category</Th>
                    <Th>SKU</Th>
                    <Th className="text-right">Cost</Th>
                    <Th className="text-right">Price</Th>
                    <Th className="text-right">Qty</Th>
                    <Th>Issues</Th>
                  </Tr>
                </THead>
                <TBody>
                  {reviewRows.map(({ row, included }) => (
                    <Tr key={row.rowNumber} className={!row.valid ? "bg-danger-soft/40" : undefined}>
                      <Td>
                        <input type="checkbox" checked={included} onChange={() => toggleRow(row.rowNumber)} className="rounded border-border" />
                      </Td>
                      <Td className="tabular text-ink-500">{row.rowNumber}</Td>
                      <Td className="font-medium">{row.name ?? "—"}</Td>
                      <Td className="text-ink-500">{row.category ?? "—"}</Td>
                      <Td className="text-ink-500">{row.sku ?? "—"}</Td>
                      <Td className="tabular text-right text-ink-500">{row.costPrice != null ? row.costPrice.toFixed(2) : "—"}</Td>
                      <Td className="tabular text-right text-ink-500">{row.sellingPrice != null ? row.sellingPrice.toFixed(2) : "—"}</Td>
                      <Td className="tabular text-right text-ink-500">{row.quantity ?? "—"}</Td>
                      <Td>
                        {row.errors.length > 0 && (
                          <span className="flex items-center gap-1 text-xs text-danger">
                            <AlertCircle size={12} /> {row.errors.join("; ")}
                          </span>
                        )}
                      </Td>
                    </Tr>
                  ))}
                </TBody>
              </Table>
            </div>
          </Card>

          {confirmError && <p className="text-sm text-danger">{confirmError}</p>}

          <div className="flex gap-2">
            <Button onClick={handleConfirm} disabled={includedCount === 0 || confirming} className="flex-1">
              {confirming ? "Importing..." : `Confirm import (${includedCount})`}
            </Button>
            <Button variant="secondary" onClick={startOver} disabled={confirming}>
              Start over
            </Button>
          </div>
        </>
      )}

      {step === "done" && result && (
        <Card className="p-6">
          <div className="flex flex-col items-center gap-3 text-center">
            <CheckCircle2 size={32} className="text-success" />
            <p className="text-lg font-semibold text-ink-900">
              {result.importedCount} product{result.importedCount === 1 ? "" : "s"} imported
            </p>
            {result.skippedCount > 0 && (
              <p className="text-sm text-ink-500">{result.skippedCount} row{result.skippedCount === 1 ? "" : "s"} skipped</p>
            )}
          </div>

          {result.skipped.length > 0 && (
            <div className="mt-4 flex flex-col gap-1 rounded-lg border border-border p-3 text-sm">
              {result.skipped.map((s) => (
                <p key={s.rowNumber} className="text-ink-700">
                  Row {s.rowNumber} ({s.name ?? "unnamed"}): <span className="text-danger">{s.reason}</span>
                </p>
              ))}
            </div>
          )}

          <div className="mt-5 flex gap-2">
            <Link href="/dashboard/inventory" className="flex-1">
              <Button className="w-full">Go to Inventory</Button>
            </Link>
            <Button variant="secondary" onClick={startOver}>
              Import more
            </Button>
          </div>
        </Card>
      )}
    </div>
  );
}
