"use client";

import { useState } from "react";
import Link from "next/link";
import { useAuth } from "@/lib/auth";
import { ApiError, Industry } from "@/lib/api";
import { decodeGoogleCredential } from "@/lib/google";
import FormField from "@/components/FormField";
import GoogleButton from "@/components/GoogleButton";
import Button from "@/components/ui/Button";
import Card from "@/components/ui/Card";

const INDUSTRIES: { value: Industry; label: string }[] = [
  { value: "RETAIL", label: "Retail / Handmade Products" },
  { value: "SALON", label: "Salon / Hair & Beauty" },
  { value: "RESTAURANT", label: "Restaurant / Food" },
  { value: "SCHOOL", label: "School" },
  { value: "OTHER", label: "Other" },
];

export default function RegisterPage() {
  const { register, registerWithGoogle } = useAuth();

  // Set once "Continue with Google" succeeds — switches the form into
  // "just need your business details" mode instead of the full form.
  const [googleCredential, setGoogleCredential] = useState<string | null>(null);
  const googleUser = googleCredential ? decodeGoogleCredential(googleCredential) : null;

  const [form, setForm] = useState({
    businessName: "",
    industry: "RETAIL" as Industry,
    location: "",
    contactPhone: "",
    ownerFullName: "",
    email: "",
    password: "",
  });
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function set<K extends keyof typeof form>(key: K, value: string) {
    setForm((f) => ({ ...f, [key]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await register(form);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleGoogleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!googleCredential) return;
    setError(null);
    setSubmitting(true);
    try {
      await registerWithGoogle({
        idToken: googleCredential,
        businessName: form.businessName,
        industry: form.industry,
        location: form.location || undefined,
        contactPhone: form.contactPhone || undefined,
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center bg-canvas px-4 py-12">
      <Card className="w-full max-w-md p-8">
        <Link href="/" className="text-sm font-semibold text-ink-900">
          Ratel
        </Link>
        <h1 className="mt-4 text-xl font-semibold text-ink-900">Register your business</h1>
        <p className="mt-1 text-sm text-ink-500">
          Creates your business account and your Owner login in one step.
        </p>

        {googleCredential ? (
          <>
            <div className="mt-6 flex items-center justify-between rounded-lg bg-accent-soft px-3 py-2 text-sm">
              <span className="text-ink-700">
                Signed in as <span className="font-medium text-ink-900">{googleUser?.email}</span>
              </span>
              <button
                type="button"
                onClick={() => setGoogleCredential(null)}
                className="font-medium text-accent-hover hover:underline"
              >
                Not you?
              </button>
            </div>

            <form onSubmit={handleGoogleSubmit} className="mt-4 flex flex-col gap-4">
              <FormField
                label="Business name"
                name="businessName"
                required
                value={form.businessName}
                onChange={(v) => set("businessName", v)}
                placeholder="e.g. Winamzua Creative Hive"
              />

              <div className="flex flex-col gap-1.5">
                <label htmlFor="industry" className="text-sm font-medium text-ink-700">
                  Industry <span className="text-danger">*</span>
                </label>
                <select
                  id="industry"
                  value={form.industry}
                  onChange={(e) => set("industry", e.target.value)}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                >
                  {INDUSTRIES.map((i) => (
                    <option key={i.value} value={i.value}>
                      {i.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <FormField
                  label="Location"
                  name="location"
                  value={form.location}
                  onChange={(v) => set("location", v)}
                  placeholder="e.g. Accra"
                />
                <FormField
                  label="Phone"
                  name="contactPhone"
                  value={form.contactPhone}
                  onChange={(v) => set("contactPhone", v)}
                  placeholder="+233 20 000 0000"
                />
              </div>

              {error && <p className="text-sm text-danger">{error}</p>}

              <Button type="submit" disabled={submitting} className="mt-1 w-full">
                {submitting ? "Creating account..." : "Complete registration"}
              </Button>
            </form>
          </>
        ) : (
          <>
            <div className="mt-6">
              <GoogleButton onCredential={setGoogleCredential} />
            </div>

            <div className="my-5 flex items-center gap-3 text-xs text-ink-500">
              <span className="h-px flex-1 bg-border" />
              or register with email
              <span className="h-px flex-1 bg-border" />
            </div>

            <form onSubmit={handleSubmit} className="flex flex-col gap-4">
              <FormField
                label="Business name"
                name="businessName"
                required
                value={form.businessName}
                onChange={(v) => set("businessName", v)}
                placeholder="e.g. Winamzua Creative Hive"
              />

              <div className="flex flex-col gap-1.5">
                <label htmlFor="industry" className="text-sm font-medium text-ink-700">
                  Industry <span className="text-danger">*</span>
                </label>
                <select
                  id="industry"
                  value={form.industry}
                  onChange={(e) => set("industry", e.target.value)}
                  className="rounded-lg border border-border bg-surface px-3 py-2 text-sm text-ink-900 transition focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/20"
                >
                  {INDUSTRIES.map((i) => (
                    <option key={i.value} value={i.value}>
                      {i.label}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <FormField
                  label="Location"
                  name="location"
                  value={form.location}
                  onChange={(v) => set("location", v)}
                  placeholder="e.g. Accra"
                />
                <FormField
                  label="Phone"
                  name="contactPhone"
                  value={form.contactPhone}
                  onChange={(v) => set("contactPhone", v)}
                  placeholder="+233 20 000 0000"
                />
              </div>

              <hr className="my-1 border-border" />

              <FormField
                label="Your full name"
                name="ownerFullName"
                required
                value={form.ownerFullName}
                onChange={(v) => set("ownerFullName", v)}
              />

              <FormField
                label="Email"
                name="email"
                type="email"
                required
                value={form.email}
                onChange={(v) => set("email", v)}
              />

              <FormField
                label="Password"
                name="password"
                type="password"
                required
                value={form.password}
                onChange={(v) => set("password", v)}
                placeholder="At least 8 characters"
              />

              {error && <p className="text-sm text-danger">{error}</p>}

              <Button type="submit" disabled={submitting} className="mt-1 w-full">
                {submitting ? "Creating account..." : "Create business account"}
              </Button>
            </form>
          </>
        )}

        <p className="mt-5 text-center text-sm text-ink-500">
          Already have an account?{" "}
          <Link href="/login" className="font-medium text-accent-hover hover:underline">
            Log in
          </Link>
        </p>
      </Card>
    </main>
  );
}
