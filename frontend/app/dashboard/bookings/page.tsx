"use client";

import { useEffect, useState, useCallback } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { CalendarCheck2, Settings, MessageCircle, Plus } from "lucide-react";
import { useAuth } from "@/lib/auth";
import {
  api,
  ApiError,
  BookingListItem,
  CreateStaffBookingPayload,
  Customer,
  ServiceCatalogItem,
  ServicePackage,
  UserSummary,
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

export default function BookingsPage() {
  const { session, loading } = useAuth();
  const router = useRouter();

  const [bookings, setBookings] = useState<BookingListItem[]>([]);
  const [catalog, setCatalog] = useState<ServiceCatalogItem[]>([]);
  const [packages, setPackages] = useState<ServicePackage[]>([]);
  const [customers, setCustomers] = useState<Customer[]>([]);
  const [staff, setStaff] = useState<UserSummary[]>([]);
  const [fetching, setFetching] = useState(true);
  const [statusFilter, setStatusFilter] = useState<string>("");
  const [error, setError] = useState<string | null>(null);
  const [showAddBooking, setShowAddBooking] = useState(false);

  const loadBookings = useCallback(async () => {
    if (!session) return;
    try {
      const data = await api.listBookings(session.token, statusFilter || undefined);
      setBookings(data);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Couldn't load bookings.");
    }
  }, [session, statusFilter]);

  const loadFormData = useCallback(async () => {
    if (!session) return;
    const [cat, pkgs, custs, users] = await Promise.all([
      api.listServiceCatalog(session.token, true),
      api.listServicePackages(session.token),
      api.listCustomers(session.token),
      api.listUsers(session.token),
    ]);
    setCatalog(cat);
    setPackages(pkgs.filter((p) => p.active));
    setCustomers(custs);
    setStaff(users);
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
          <TableSkeleton cols={7} />
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
              </Tr>
            </THead>
            <TBody>
              {bookings.map((b) => {
                const whatsappLink = b.customerWhatsapp
                  ? `https://wa.me/${b.customerWhatsapp.replace(/[^0-9]/g, "")}`
                  : null;
                return (
                  <Tr key={b.bookingNumber}>
                    <Td className="tabular font-medium">#{b.bookingNumber}</Td>
                    <Td className="text-ink-500">
                      <div className="flex items-center gap-1.5">
                        <span>{b.customerName}</span>
                        {whatsappLink && (
                          <a
                            href={whatsappLink}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-ink-400 hover:text-accent"
                            aria-label="Message on WhatsApp"
                          >
                            <MessageCircle size={14} />
                          </a>
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
            catalog={catalog}
            packages={packages}
            customers={customers}
            staff={staff}
            onSubmit={handleCreateBooking}
          />
        </Modal>
      )}
    </div>
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
