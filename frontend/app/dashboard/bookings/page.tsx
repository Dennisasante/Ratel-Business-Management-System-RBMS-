"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { CalendarCheck2, Settings, MessageCircle, Plus, Eye, CalendarClock, UserCheck } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  BookingListItem,
  CreateStaffBookingPayload,
  ServiceCatalogItem,
  ServicePackage,
  StaffMember,
} from "@/lib/api";
import Modal from "@/components/Modal";
import StaffBookingForm from "@/components/StaffBookingForm";
import PageHeader from "@/components/ui/PageHeader";
import Card from "@/components/ui/Card";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import EmptyState from "@/components/ui/EmptyState";
import TableSkeleton from "@/components/ui/TableSkeleton";
import { Table, THead, TBody, Tr, Th, Td } from "@/components/ui/Table";

const STATUS_LABELS: Record<string, string> = {
  RECEIVED: "Received",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  PICKED_UP: "Picked up",
  CANCELLED: "Cancelled",
};

const STATUS_TONES: Record<string, "neutral" | "accent" | "success" | "danger" | "info" | "violet"> = {
  RECEIVED: "info",
  IN_PROGRESS: "accent",
  COMPLETED: "success",
  PICKED_UP: "violet",
  CANCELLED: "danger",
};

const PAYMENT_LABELS: Record<string, string> = {
  UNPAID: "Unpaid",
  PAID: "Paid",
  FAILED: "Payment failed",
  PAY_IN_PERSON: "Pay in person",
};

const PAYMENT_TONES: Record<string, "neutral" | "success" | "danger"> = {
  UNPAID: "neutral",
  PAID: "success",
  FAILED: "danger",
  PAY_IN_PERSON: "neutral",
};

function whatsappLinkFor(customerWhatsapp: string | null): string | null {
  return customerWhatsapp ? `https://wa.me/${customerWhatsapp.replace(/[^0-9]/g, "")}` : null;
}

export default function BookingsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [bookings, setBookings] = useState<BookingListItem[]>([]);
  const [catalog, setCatalog] = useState<ServiceCatalogItem[]>([]);
  const [packages, setPackages] = useState<ServicePackage[]>([]);
  const [staff, setStaff] = useState<StaffMember[]>([]);
  const [fetching, setFetching] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [showAddBooking, setShowAddBooking] = useState(false);
  const [detailBooking, setDetailBooking] = useState<BookingListItem | null>(null);

  const loadBookings = useCallback(async () => {
    if (!session) return;
    try {
      const data = await api.listBookings(session.token, statusFilter || undefined);
      setBookings(data);
      // Keep an open detail modal in sync with the freshest row data.
      setDetailBooking((prev) => (prev ? data.find((b) => b.id === prev.id) ?? null : null));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load bookings.");
    }
  }, [session, statusFilter]);

  const loadFormData = useCallback(async () => {
    if (!session) return;
    const [cat, pkgs, members] = await Promise.all([
      api.listServiceCatalog(session.token, true),
      api.listServicePackages(session.token),
      api.listStaffMembers(session.token),
    ]);
    setCatalog(cat);
    setPackages(pkgs.filter((p) => p.active));
    setStaff(members);
  }, [session]);

  useEffect(() => {
    if (!loading && !session) router.push("/login");
  }, [loading, session, router]);

  useEffect(() => {
    if (!session) return;
    Promise.all([loadBookings(), loadFormData()]).finally(() => setFetching(false));
  }, [session, loadBookings, loadFormData]);

  async function handleCreateBooking(payload: CreateStaffBookingPayload) {
    if (!session) return;
    await api.createStaffBooking(session.token, payload);
    setShowAddBooking(false);
    await loadBookings();
  }

  if (loading || !session) {
    return <p className="text-sm text-ink-500">Loading...</p>;
  }

  return (
    <div className="flex flex-col gap-6">
      <PageHeader
        title="Bookings"
        subtitle="Everyone who's booked online — who, what, when, and whether they've paid."
        actions={
          <div className="flex items-center gap-2">
            <Button onClick={() => setShowAddBooking(true)} disabled={catalog.length === 0 && packages.length === 0}>
              <Plus size={16} /> New booking
            </Button>
            {session.role === "OWNER" && (
              <Link href="/dashboard/bookings/settings">
                <Button variant="secondary">
                  <Settings size={16} /> Booking settings
                </Button>
              </Link>
            )}
          </div>
        }
      />

      <div className="flex flex-wrap items-center gap-2">
        <FilterChip label="All statuses" active={statusFilter === ""} onClick={() => setStatusFilter("")} />
        {Object.keys(STATUS_LABELS).map((s) => (
          <FilterChip key={s} label={STATUS_LABELS[s]} active={statusFilter === s} onClick={() => setStatusFilter(s)} />
        ))}
      </div>

      <Card>
        {error && <p className="px-5 pt-4 text-sm text-danger">{error}</p>}
        {fetching ? (
          <TableSkeleton cols={8} />
        ) : bookings.length === 0 ? (
          <EmptyState
            icon={CalendarCheck2}
            title="No bookings yet"
            description="Bookings made through your booking page will show up here."
          />
        ) : (
          <Table>
            <THead>
              <Tr>
                <Th>Booking</Th>
                <Th>Customer</Th>
                <Th>Service</Th>
                <Th>When</Th>
                <Th>Status</Th>
                <Th>Payment</Th>
                <Th>Staff</Th>
                <Th className="text-right">Actions</Th>
              </Tr>
            </THead>
            <TBody>
              {bookings.map((b) => {
                const whatsappLink = whatsappLinkFor(b.customerWhatsapp);
                return (
                  <Tr key={b.id}>
                    <Td className="tabular font-medium">
                      <button className="hover:underline" onClick={() => setDetailBooking(b)}>
                        #{b.bookingNumber}
                      </button>
                    </Td>
                    <Td className="text-ink-500">
                      <div className="flex items-center gap-1.5">
                        <span>{b.customerName}</span>
                        {b.arrivedAt && (
                          <span title={`Arrived ${new Date(b.arrivedAt).toLocaleString()}`}>
                            <UserCheck size={14} className="text-success" />
                          </span>
                        )}
                      </div>
                    </Td>
                    <Td className="text-ink-500">{b.serviceName ?? "—"}</Td>
                    <Td className="tabular text-ink-500">
                      {b.scheduledAt ? new Date(b.scheduledAt).toLocaleString() : "—"}
                    </Td>
                    <Td>
                      {b.orderStatus ? (
                        <Badge tone={STATUS_TONES[b.orderStatus] ?? "neutral"}>
                          {STATUS_LABELS[b.orderStatus] ?? b.orderStatus}
                        </Badge>
                      ) : (
                        "—"
                      )}
                    </Td>
                    <Td>
                      <Badge tone={PAYMENT_TONES[b.paymentStatus] ?? "neutral"}>
                        {PAYMENT_LABELS[b.paymentStatus] ?? b.paymentStatus}
                      </Badge>
                    </Td>
                    <Td className="text-ink-500">{b.assignedStaffName ?? "Unassigned"}</Td>
                    <Td>
                      <div className="flex items-center justify-end gap-1">
                        <button
                          onClick={() => setDetailBooking(b)}
                          className="rounded-md p-1.5 text-ink-500 hover:bg-canvas hover:text-ink-900"
                          aria-label="View details"
                          title="View details"
                        >
                          <Eye size={16} />
                        </button>
                        {whatsappLink && (
                          <a
                            href={whatsappLink}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="rounded-md p-1.5 text-ink-500 hover:bg-canvas hover:text-success"
                            aria-label="Message on WhatsApp"
                            title="Message on WhatsApp"
                          >
                            <MessageCircle size={16} />
                          </a>
                        )}
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </TBody>
          </Table>
        )}
      </Card>

      {showAddBooking && (
        <Modal title="New booking" onClose={() => setShowAddBooking(false)}>
          <StaffBookingForm
            token={session.token}
            catalog={catalog}
            packages={packages}
            staff={staff}
            onSubmit={handleCreateBooking}
          />
        </Modal>
      )}

      {detailBooking && (
        <BookingDetailModal
          booking={detailBooking}
          token={session.token}
          onClose={() => setDetailBooking(null)}
          onChanged={loadBookings}
        />
      )}
    </div>
  );
}

function BookingDetailModal({
  booking,
  token,
  onClose,
  onChanged,
}: {
  booking: BookingListItem;
  token: string;
  onClose: () => void;
  onChanged: () => Promise<void>;
}) {
  const [rescheduling, setRescheduling] = useState(false);
  const [newWhen, setNewWhen] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const whatsappLink = whatsappLinkFor(booking.customerWhatsapp);
  const canReschedule = booking.orderStatus !== "CANCELLED" && booking.orderStatus !== "PICKED_UP";

  async function handleReschedule(e: React.FormEvent) {
    e.preventDefault();
    if (!newWhen) return;
    setError(null);
    setBusy(true);
    try {
      await api.rescheduleBookingById(token, booking.id, new Date(newWhen).toISOString());
      setRescheduling(false);
      setNewWhen("");
      await onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't reschedule this booking.");
    } finally {
      setBusy(false);
    }
  }

  async function handleMarkArrived() {
    setError(null);
    setBusy(true);
    try {
      await api.markBookingArrived(token, booking.id);
      await onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't update this booking.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal title={`Booking #${booking.bookingNumber}`} onClose={onClose}>
      <div className="flex flex-col gap-4">
        <div className="flex items-center gap-2">
          {booking.orderStatus && (
            <Badge tone={STATUS_TONES[booking.orderStatus] ?? "neutral"}>
              {STATUS_LABELS[booking.orderStatus] ?? booking.orderStatus}
            </Badge>
          )}
          <Badge tone={PAYMENT_TONES[booking.paymentStatus] ?? "neutral"}>
            {PAYMENT_LABELS[booking.paymentStatus] ?? booking.paymentStatus}
          </Badge>
          {booking.arrivedAt && (
            <Badge tone="success">
              <UserCheck size={12} /> Arrived
            </Badge>
          )}
        </div>

        <div className="flex flex-col gap-1 text-sm">
          <p className="font-medium text-ink-900">{booking.customerName}</p>
          {booking.customerEmail && <p className="text-ink-500">{booking.customerEmail}</p>}
          {booking.customerWhatsapp && <p className="text-ink-500">{booking.customerWhatsapp}</p>}
          {booking.customerLocation && <p className="text-ink-500">{booking.customerLocation}</p>}
        </div>

        <div className="flex flex-col gap-1.5 rounded-lg border border-border p-3 text-sm">
          <div className="flex items-center justify-between">
            <span className="text-ink-500">Service</span>
            <span className="font-medium text-ink-900">{booking.serviceName ?? "—"}</span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-ink-500">Scheduled for</span>
            <span className="tabular font-medium text-ink-900">
              {booking.scheduledAt ? new Date(booking.scheduledAt).toLocaleString() : "—"}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-ink-500">Price</span>
            <span className="tabular font-medium text-ink-900">
              {booking.price != null ? `GH₵${booking.price.toFixed(2)}` : "—"}
            </span>
          </div>
          <div className="flex items-center justify-between">
            <span className="text-ink-500">Staff</span>
            <span className="font-medium text-ink-900">{booking.assignedStaffName ?? "Unassigned"}</span>
          </div>
          {booking.arrivedAt && (
            <div className="flex items-center justify-between">
              <span className="text-ink-500">Arrived</span>
              <span className="tabular font-medium text-ink-900">{new Date(booking.arrivedAt).toLocaleString()}</span>
            </div>
          )}
        </div>

        {error && <p className="text-sm text-danger">{error}</p>}

        {rescheduling ? (
          <form onSubmit={handleReschedule} className="flex flex-col gap-3 rounded-lg bg-canvas p-3">
            <label className="text-sm font-medium text-ink-700">New date &amp; time</label>
            <input
              type="datetime-local"
              required
              value={newWhen}
              onChange={(e) => setNewWhen(e.target.value)}
              className="rounded-lg border border-border bg-surface px-3 py-2 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
            />
            <div className="flex gap-2">
              <Button type="submit" disabled={busy} className="flex-1">
                {busy ? "Saving..." : "Save new time"}
              </Button>
              <Button type="button" variant="ghost" onClick={() => setRescheduling(false)}>
                Cancel
              </Button>
            </div>
          </form>
        ) : (
          <div className="flex flex-wrap gap-2">
            {!booking.arrivedAt && (
              <Button variant="secondary" onClick={handleMarkArrived} disabled={busy}>
                <UserCheck size={15} className="mr-1.5" />
                {busy ? "Updating..." : "Mark arrived"}
              </Button>
            )}
            {canReschedule && (
              <Button variant="secondary" onClick={() => setRescheduling(true)}>
                <CalendarClock size={15} className="mr-1.5" /> Reschedule
              </Button>
            )}
          </div>
        )}

        {whatsappLink && (
          <a
            href={whatsappLink}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center justify-center gap-2 rounded-lg bg-success px-4 py-2 text-sm font-medium text-white hover:bg-success/90"
          >
            <MessageCircle size={15} />
            Message customer on WhatsApp
          </a>
        )}
      </div>
    </Modal>
  );
}

function FilterChip({ label, active, onClick }: { label: string; active: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      className={`rounded-full border px-3 py-1 text-xs font-medium transition ${
        active
          ? "border-accent bg-accent-soft text-accent-hover"
          : "border-border bg-surface text-ink-700 hover:border-border-strong"
      }`}
    >
      {label}
    </button>
  );
}
