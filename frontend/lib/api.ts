// Thin fetch wrapper. Requests go through Next's /api/* rewrite (see next.config.js),
// which proxies to the Spring Boot backend — so no CORS setup is needed in the browser.

export type Industry = "SALON" | "RETAIL" | "RESTAURANT" | "SCHOOL" | "OTHER";

export interface AuthResponse {
  token: string;
  userId: string;
  businessId: string;
  businessName: string;
  fullName: string;
  email: string;
  role: string;
  mustChangePassword: boolean;
}

export interface RegisterBusinessPayload {
  businessName: string;
  industry: Industry;
  location?: string;
  contactPhone?: string;
  ownerFullName: string;
  email: string;
  password: string;
}

export interface LoginPayload {
  email: string;
  password: string;
}

export interface BusinessSummary {
  id: string;
  name: string;
  slug: string;
  industry: string;
  location: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  currency: string;
  subscriptionPlan: string;
  enabledModules: string[];
  logoUrl: string | null;
  billingStatus: "TRIALING" | "ACTIVE" | "READ_ONLY";
  planFeatures: PlanFeature[];
}

export interface UserSummary {
  id: string;
  fullName: string;
  email: string;
  role: string;
  active: boolean;
  commissionRate: number;
  createdAt: string;
}

export type StaffRole = "OWNER" | "MANAGER" | "SALES_PERSON" | "ACCOUNTANT" | "STAFF";

export interface CreateStaffPayload {
  fullName: string;
  email: string;
  role: StaffRole;
  temporaryPassword: string;
}

export interface AdminResetPasswordResponse {
  temporaryPassword: string;
}

export interface PlatformAuditLogEntry {
  id: string;
  action: string;
  businessId: string | null;
  businessName: string | null;
  adminName: string;
  createdAt: string;
}

export interface DayCount {
  date: string;
  count: number;
}

export interface PlatformBillingStatusCount {
  status: string;
  count: number;
}

export interface PlatformPlanMixEntry {
  planName: string;
  businessCount: number;
  mrr: number;
}

export interface PlatformStats {
  totalBusinesses: number;
  activeBusinesses: number;
  totalUsers: number;
  totalPlatformRevenue: number;
  signupsByDay: DayCount[];
  activityByDay: DayCount[];
  billingStatusBreakdown: PlatformBillingStatusCount[];
  planMix: PlatformPlanMixEntry[];
  totalBookings: number;
  totalEcommerceOrders: number;
  totalCustomWigRequests: number;
  totalServiceOrders: number;
}

export type MovementType = "ADD" | "REMOVE" | "ADJUST";

export interface Product {
  id: string;
  name: string;
  category: string | null;
  categoryId: string | null;
  sku: string | null;
  costPrice: number;
  sellingPrice: number;
  quantity: number;
  lowStockThreshold: number;
  lowStock: boolean;
  supplierName: string | null;
  imageUrl: string | null;
  active: boolean;
  publishToWebsite: boolean;
  syncedToWebsite: boolean;
  createdAt: string;
}

export interface ProductPayload {
  name: string;
  category?: string;
  categoryId?: string;
  sku?: string;
  costPrice?: number;
  sellingPrice?: number;
  quantity?: number;
  lowStockThreshold?: number;
  supplierName?: string;
  imageUrl?: string;
  publishToWebsite?: boolean;
}

export interface StockAdjustmentPayload {
  movementType: MovementType;
  quantity: number;
  note?: string;
}

export interface StockMovement {
  id: string;
  movementType: MovementType;
  quantityChange: number;
  resultingQuantity: number;
  note: string | null;
  createdAt: string;
}

export interface Customer {
  id: string;
  fullName: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
  totalSpent: number;
  purchaseCount: number;
  createdAt: string;
}

export interface CustomerPayload {
  fullName: string;
  phone?: string;
  email?: string;
  notes?: string;
}

export type PaymentMethod = "CASH" | "MOBILE_MONEY" | "CARD" | "BANK_TRANSFER";

export type SaleItemType = "PRODUCT" | "SERVICE";

export interface SaleItemPayload {
  // Exactly one of these two must be set.
  productId?: string;
  serviceCatalogId?: string;
  quantity: number;
  discountAmount?: number;
}

export interface SalePayload {
  customerId?: string;
  paymentMethod: PaymentMethod;
  items: SaleItemPayload[];
}

export interface SaleItem {
  itemType: SaleItemType;
  productId: string | null;
  serviceCatalogId: string | null;
  productName: string;
  unitPrice: number;
  quantity: number;
  discountAmount: number;
  subtotal: number;
}

export interface Sale {
  id: string;
  saleNumber: number;
  customerId: string | null;
  customerName: string | null;
  cashierName: string;
  paymentMethod: PaymentMethod;
  totalAmount: number;
  commissionAmount: number;
  items: SaleItem[];
  createdAt: string;
}

export type ExpenseCategory =
  | "RENT"
  | "UTILITIES"
  | "TRANSPORT"
  | "SUPPLIES"
  | "SALARY"
  | "MARKETING"
  | "OTHER";

export interface Expense {
  id: string;
  category: ExpenseCategory;
  description: string | null;
  amount: number;
  expenseDate: string;
  recordedByName: string;
  createdAt: string;
}

export interface ExpensePayload {
  category: ExpenseCategory;
  description?: string;
  amount: number;
  expenseDate?: string;
}

export interface ReportSummary {
  from: string;
  to: string;
  revenue: number;
  expenses: number;
  profit: number;
  salesCount: number;
  expenseCount: number;
}

export interface GoogleRegisterPayload {
  idToken: string;
  businessName: string;
  industry: Industry;
  location?: string;
  contactPhone?: string;
}

export interface ActivityLogEntry {
  id: string;
  businessId: string;
  businessName: string;
  userId: string | null;
  userName: string;
  action: string;
  entityType: string | null;
  entityId: string | null;
  createdAt: string;
}

export interface PlatformAuthResponse {
  token: string;
  adminId: string;
  fullName: string;
  email: string;
  mustChangePassword: boolean;
}

export interface Supplier {
  id: string;
  name: string;
  phone: string | null;
  email: string | null;
  notes: string | null;
  createdAt: string;
}

export interface SupplierPayload {
  name: string;
  phone?: string;
  email?: string;
  notes?: string;
}

export type PurchaseOrderStatus = "PENDING" | "RECEIVED" | "CANCELLED";

export interface PurchaseOrderItem {
  productId: string;
  productName: string;
  unitCost: number;
  quantity: number;
  subtotal: number;
}

export interface PurchaseOrder {
  id: string;
  poNumber: number;
  supplierId: string | null;
  supplierName: string | null;
  status: PurchaseOrderStatus;
  totalAmount: number;
  createdByName: string;
  items: PurchaseOrderItem[];
  createdAt: string;
  receivedAt: string | null;
}

export interface PurchaseOrderItemPayload {
  productId: string;
  quantity: number;
  unitCost: number;
}

export interface PurchaseOrderPayload {
  supplierId?: string;
  items: PurchaseOrderItemPayload[];
}

export type ServiceOrderStatus = "RECEIVED" | "IN_PROGRESS" | "COMPLETED" | "PICKED_UP" | "CANCELLED";

export interface ServiceType {
  id: string;
  name: string;
  usageCount: number;
  createdAt: string;
}

export interface ServiceTypePayload {
  name: string;
}

export interface ServiceCatalogItem {
  id: string;
  serviceTypeId: string;
  serviceTypeName: string | null;
  name: string;
  price: number;
  active: boolean;
  bookableOnline: boolean;
  durationMinutes: number;
  maxConcurrentBookings: number;
  requiresLocation: boolean;
  paymentPolicyOverride: "NONE" | "DEPOSIT" | "FULL" | null;
  createdAt: string;
}

export interface ServiceCatalogItemPayload {
  serviceTypeId: string;
  name: string;
  price: number;
  bookableOnline?: boolean;
  durationMinutes?: number;
  maxConcurrentBookings?: number;
  requiresLocation?: boolean;
  paymentPolicyOverride?: "NONE" | "DEPOSIT" | "FULL" | "";
}

export interface ServicePackageItem {
  id: string;
  serviceCatalogId: string;
  serviceCatalogName: string | null;
  quantity: number;
}

export interface ServicePackageItemPayload {
  serviceCatalogId: string;
  quantity?: number;
}

export interface ServicePackage {
  id: string;
  serviceTypeId: string;
  serviceTypeName: string | null;
  name: string;
  description: string | null;
  price: number;
  active: boolean;
  bookableOnline: boolean;
  durationMinutes: number;
  maxConcurrentBookings: number;
  paymentPolicyOverride: "NONE" | "DEPOSIT" | "FULL" | null;
  items: ServicePackageItem[];
}

export interface ServicePackagePayload {
  serviceTypeId: string;
  name: string;
  description?: string;
  price: number;
  bookableOnline?: boolean;
  durationMinutes?: number;
  maxConcurrentBookings?: number;
  items: ServicePackageItemPayload[];
  paymentPolicyOverride?: "NONE" | "DEPOSIT" | "FULL" | "";
}

export interface ServiceOrder {
  id: string;
  orderNumber: number;
  serviceTypeId: string;
  serviceTypeName: string | null;
  status: ServiceOrderStatus;
  customerId: string | null;
  customerName: string | null;
  serviceCatalogId: string | null;
  serviceCatalogName: string | null;
  notes: string | null;
  price: number;
  discountAmount: number;
  assignedStaffId: string | null;
  assignedStaffName: string | null;
  receivedAt: string;
  scheduledAt: string | null;
  pickedUpAt: string | null;
  readyEmailSentAt: string | null;
  createdByName: string;
  createdAt: string;
  updatedAt: string;
  bookingPaymentStatus: string | null;
  bookingWhatsappLink: string | null;
}

export interface ServiceOrderPayload {
  serviceTypeId: string;
  customerId?: string;
  serviceCatalogId?: string;
  notes?: string;
  price?: number;
  discountAmount?: number;
  assignedStaffId?: string;
  scheduledAt?: string;
}

export interface ServiceOrderUpdatePayload {
  notes?: string;
  price: number;
  discountAmount?: number;
  assignedStaffId?: string;
  scheduledAt?: string;
}

export interface ServiceOrderPhoto {
  id: string;
  url: string;
  createdAt: string;
}

export interface ServiceOrderReport {
  from: string;
  to: string;
  revenueByType: { serviceTypeId: string; serviceTypeName: string | null; revenue: number }[];
  statusCounts: Record<ServiceOrderStatus, number>;
  avgTurnaroundHours: number;
}

export interface ProductCategory {
  id: string;
  name: string;
  productCount: number;
  createdAt: string;
}

export interface ProductCategoryPayload {
  name: string;
}

export interface BusinessUpdatePayload {
  name: string;
  industry: Industry;
  location?: string;
  contactEmail?: string;
  contactPhone?: string;
}

export interface StaffCommission {
  userId: string;
  userName: string;
  commissionRate: number;
  salesCount: number;
  totalSales: number;
  commissionEarned: number;
}

export interface CreatePlatformAdminPayload {
  fullName: string;
  email: string;
  temporaryPassword: string;
}

export interface PlatformAdminSummary {
  id: string;
  fullName: string;
  email: string;
  createdAt: string;
}

export type PlanFeature = "BOOKING_WIDGET" | "WOOCOMMERCE_SYNC" | "CUSTOM_WIG_REQUESTS";

export interface SubscriptionPlan {
  id: string;
  name: string;
  price: number;
  currency: string;
  billingPeriodDays: number;
  active: boolean;
  sortOrder: number;
  features: PlanFeature[];
}

export interface SubscriptionPlanPayload {
  name: string;
  price: number;
  currency: string;
  billingPeriodDays: number;
  sortOrder: number;
  features: PlanFeature[];
}

export interface PlatformBillingSettings {
  trialDays: number;
  usdDisplayRate: number | null;
  paystackPublicKey: string | null;
  paystackSecretConfigured: boolean;
}

export interface PlatformBillingSettingsPayload {
  trialDays: number;
  usdDisplayRate: number | null;
  paystackPublicKey?: string;
  paystackSecretKey?: string;
}

export interface BillingStatus {
  billingStatus: "TRIALING" | "ACTIVE" | "READ_ONLY";
  plan: SubscriptionPlan | null;
  trialEndsAt: string | null;
  currentPeriodEndsAt: string | null;
  daysRemaining: number;
  usdDisplayRate: number | null;
}

export interface CheckoutResponse {
  accessCode: string;
  reference: string;
}

export interface VerifyPaymentResponse {
  success: boolean;
  billingStatus: string | null;
  currentPeriodEndsAt: string | null;
  message: string;
}

export interface BusinessIntegrations {
  paystackPublicKey: string | null;
  paystackSecretConfigured: boolean;
  paystackSecretMasked: string | null;
  woocommerceSiteUrl: string | null;
  woocommerceConfigured: boolean;
  woocommerceConsumerKeyMasked: string | null;
  woocommerceWebhookRegistered: boolean;
  whatsappNotifyNumber: string | null;
  testMode: boolean;
}

// Every field: undefined/omitted = leave unchanged, "" = clear, else = set.
export interface BusinessIntegrationsPayload {
  paystackPublicKey?: string;
  paystackSecretKey?: string;
  woocommerceSiteUrl?: string;
  woocommerceConsumerKey?: string;
  woocommerceConsumerSecret?: string;
  whatsappNotifyNumber?: string;
  testMode?: boolean;
}

export interface BlackoutDate {
  id: string;
  date: string;
  label: string | null;
}

export interface WorkingHoursEntry {
  dayOfWeek: number; // ISO weekday, 1=Mon..7=Sun
  startTime: string;
  endTime: string;
}

export interface BookingSettings {
  paymentPolicy: "NONE" | "DEPOSIT" | "FULL";
  depositPercent: number;
  allowPayInPerson: boolean;
  cancellationCutoffHours: number;
  workingHours: WorkingHoursEntry[];
}

// Same "leave unchanged unless told otherwise" convention as
// BusinessIntegrationsPayload — except workingHours, which always fully
// replaces the current set when present (omit it to leave hours untouched).
export interface BookingSettingsPayload {
  paymentPolicy?: "NONE" | "DEPOSIT" | "FULL";
  depositPercent?: number;
  allowPayInPerson?: boolean;
  cancellationCutoffHours?: number;
  workingHours?: WorkingHoursEntry[];
}

export interface BookingListItem {
  bookingNumber: number;
  customerName: string;
  // Nullable: a staff-entered booking may have only a name on hand — the
  // public widget always requires both.
  customerEmail: string | null;
  customerWhatsapp: string | null;
  customerLocation: string | null;
  serviceName: string | null;
  orderStatus: string | null;
  scheduledAt: string | null;
  price: number | null;
  paymentStatus: string;
  assignedStaffName: string | null;
  createdAt: string;
}

export interface CreateStaffBookingPayload {
  // Exactly one of these two must be set.
  serviceCatalogId?: string;
  packageId?: string;
  customerId?: string;
  customerName?: string;
  customerEmail?: string;
  customerWhatsapp?: string;
  scheduledAt: string;
  notes?: string;
  customerLocation?: string;
  assignedStaffId?: string;
  paymentStatus: "UNPAID" | "PAID" | "PAY_IN_PERSON";
}

export interface TestConnectionResult {
  success: boolean;
  message: string;
}

export interface BookingDetail {
  bookingNumber: number;
  businessName: string | null;
  serviceName: string | null;
  status: string;
  scheduledAt: string;
  price: number;
  paymentStatus: string;
  customerName: string;
  amountDue: number | null;
  currency: string;
  businessWhatsappLink: string | null;
  customerLocation: string | null;
  cancellationCutoffHours: number;
}

export type EcommerceOrderStatus = "RECEIVED" | "PROCESSING" | "READY" | "COMPLETED" | "CANCELLED";

export interface EcommerceOrder {
  id: string;
  orderNumber: string;
  status: EcommerceOrderStatus;
  customerName: string | null;
  customerEmail: string | null;
  customerPhone: string | null;
  totalAmount: number;
  currency: string;
  itemCount: number;
  whatsappLink: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface EcommerceOrderItem {
  productName: string;
  quantity: number;
  unitPrice: number;
}

export interface EcommerceOrderDetail {
  id: string;
  orderNumber: string;
  status: EcommerceOrderStatus;
  customerName: string | null;
  customerEmail: string | null;
  customerPhone: string | null;
  totalAmount: number;
  currency: string;
  whatsappLink: string | null;
  items: EcommerceOrderItem[];
  createdAt: string;
  updatedAt: string;
}

export interface CustomItemAttributeOption {
  id: string;
  label: string;
  priceModifier: number;
  sortOrder: number;
  requiresManualQuote: boolean;
}

export type AttributeSelectionType = "SINGLE" | "MULTIPLE";

export interface CustomItemAttribute {
  id: string;
  name: string;
  sortOrder: number;
  selectionType: AttributeSelectionType;
  stepGroup: string | null;
  options: CustomItemAttributeOption[];
}

export interface CustomItemAttributeOptionPayload {
  label: string;
  priceModifier: number;
  sortOrder?: number;
  requiresManualQuote?: boolean;
}

export interface CustomItemAttributePayload {
  name: string;
  sortOrder?: number;
  selectionType?: AttributeSelectionType;
  stepGroup?: string | null;
  options: CustomItemAttributeOptionPayload[];
}

export type CustomWigRequestStatus = "SUBMITTED" | "QUOTED" | "ACCEPTED" | "DECLINED";

export interface CustomWigRequest {
  id: string;
  requestNumber: number;
  customerName: string;
  customerEmail: string;
  customerWhatsapp: string;
  estimatedPrice: number;
  status: CustomWigRequestStatus;
  finalPrice: number | null;
  whatsappLink: string | null;
  createdAt: string;
}

export interface CustomWigSelection {
  attributeName: string;
  optionLabel: string;
  priceModifier: number;
  requiresManualQuote: boolean;
}

// Public/hosted-page side — submission input, distinct from the owner-facing
// snapshot shape above (CustomWigSelection).
export interface CustomWigSelectionInput {
  attributeId: string;
  optionId: string;
}

export interface CustomWigRequestCreated {
  requestNumber: number;
  message: string;
  estimatedPrice: number;
}

// Hosted custom-order page (ratel.app/order/{slug}) config — mirrors
// PublicCustomWigConfigResponse. Reuses the same CustomItemAttribute/
// CustomItemAttributeOption shapes the owner-side attribute manager uses,
// since the public DTO's fields are identical.
export interface PublicCustomWigConfig {
  businessId: string;
  businessName: string;
  enabled: boolean;
  currency: string;
  attributes: CustomItemAttribute[];
  businessWhatsappLink: string | null;
}

// The single hosted hub link (ratel.app/start/{slug}) a business puts in its
// WhatsApp bio — reports which entry points are actually usable today.
export interface StartHubConfig {
  businessName: string;
  bookingEnabled: boolean;
  customOrderEnabled: boolean;
  businessWhatsappLink: string | null;
}

export interface CustomWigRequestDetail {
  id: string;
  requestNumber: number;
  customerName: string;
  customerEmail: string;
  customerWhatsapp: string;
  selections: CustomWigSelection[];
  estimatedPrice: number;
  inspirationPhotoUrl: string | null;
  notes: string | null;
  status: CustomWigRequestStatus;
  finalPrice: number | null;
  ownerMessage: string | null;
  whatsappLink: string | null;
  createdAt: string;
}

export interface BookingWidgetConfig {
  businessId: string;
  businessName: string;
  enabled: boolean;
  currency: string;
  paystackPublicKey: string | null;
  paymentPolicy: "NONE" | "DEPOSIT" | "FULL";
  depositPercent: number;
  allowPayInPerson: boolean;
  workingHours: WorkingHoursEntry[];
  businessWhatsappLink: string | null;
}

export interface BookableService {
  serviceCatalogId: string | null;
  packageId: string | null;
  serviceName: string;
  serviceTypeId: string | null;
  serviceTypeName: string | null;
  description: string | null;
  price: number;
  isPackage: boolean;
  requiresLocation: boolean;
  includedItems: string[];
  paymentPolicy: "NONE" | "DEPOSIT" | "FULL";
}

export interface CreateBookingPayload {
  serviceCatalogId?: string;
  packageId?: string;
  scheduledAt: string;
  customerName: string;
  customerEmail: string;
  customerWhatsapp: string;
  notes?: string;
  customerLocation?: string;
}

export interface BookingCreated {
  manageToken: string;
  bookingNumber: number;
  message: string;
  paymentRequired: boolean;
  amountDue: number | null;
}

export interface SubscriptionPaymentSummary {
  id: string;
  planName: string | null;
  amount: number;
  currency: string;
  status: "PENDING" | "SUCCESS" | "FAILED";
  periodStart: string | null;
  periodEnd: string | null;
  paidAt: string | null;
  createdAt: string;
}

export type PlatformBillingStatus = "TRIALING" | "ACTIVE" | "READ_ONLY";

export interface PlatformBusinessSummary {
  id: string;
  name: string;
  industry: string;
  location: string | null;
  subscriptionPlan: string;
  active: boolean;
  userCount: number;
  ownerEmail: string;
  createdAt: string;
  billingStatus: PlatformBillingStatus;
}

export interface PlatformBusinessDetail {
  id: string;
  name: string;
  industry: string;
  location: string | null;
  contactEmail: string | null;
  contactPhone: string | null;
  currency: string;
  subscriptionPlan: string;
  enabledModules: string[];
  active: boolean;
  createdAt: string;
  users: UserSummary[];
  productCount: number;
  customerCount: number;
  salesCount: number;
  totalRevenue: number;
  expenseCount: number;
  totalExpenses: number;
  billingStatus: PlatformBillingStatus;
  trialEndsAt: string | null;
  currentPeriodEndsAt: string | null;
  subscriptionPlanId: string | null;
  planName: string;
  priceOverride: number | null;
  bookingCount: number;
  ecommerceOrderCount: number;
  customWigRequestCount: number;
  serviceOrderCount: number;
  paystackConfigured: boolean;
  woocommerceConfigured: boolean;
  whatsappConfigured: boolean;
  staffByRole: Record<string, number>;
}

// Same "null = leave unchanged" convention as elsewhere — clearPlan/
// clearPriceOverride are the explicit escape hatch for setting a field back
// to "none", since a plain null can't distinguish "unchanged" from "cleared".
export interface PlatformBusinessBillingUpdatePayload {
  subscriptionPlanId?: string;
  clearPlan?: boolean;
  trialEndsAt?: string;
  priceOverride?: number;
  clearPriceOverride?: boolean;
}

class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(
  path: string,
  options: RequestInit = {},
  // Deprecated/unused — kept only so existing call sites (`api.xxx(session.token, ...)`)
  // across the app still type-check without a mass find-and-replace. Real auth
  // now travels via the httpOnly cookie, forwarded by middleware.ts as a
  // Bearer header when it proxies to the backend; the browser attaches the
  // cookie automatically on this same-origin request.
  _token?: string | null
): Promise<T> {
  const res = await fetch(path, {
    ...options,
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
      ...options.headers,
    },
  });

  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new ApiError(res.status, body.error || "Request failed");
  }

  // 204 No Content etc.
  if (res.status === 204) return undefined as T;
  return res.json() as Promise<T>;
}

// Receipts/exports: the httpOnly cookie rides along automatically on this
// same-origin request (via middleware.ts), same as request() above — no
// header to attach by hand anymore. The `token` param is unused/deprecated,
// same reasoning as request().
async function downloadFile(url: string, _token: string, filename: string, openInline = false): Promise<void> {
  const res = await fetch(url, { credentials: "same-origin" });
  if (!res.ok) {
    const body = await res.json().catch(() => ({ error: res.statusText }));
    throw new ApiError(res.status, body.error || "Download failed");
  }
  const blob = await res.blob();
  const objectUrl = URL.createObjectURL(blob);
  if (openInline) {
    window.open(objectUrl, "_blank");
  } else {
    const a = document.createElement("a");
    a.href = objectUrl;
    a.download = filename;
    document.body.appendChild(a);
    a.click();
    a.remove();
  }
  setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
}

export const api = {
  registerBusiness: (payload: RegisterBusinessPayload) =>
    request<AuthResponse>("/session/register", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  login: (payload: LoginPayload) =>
    request<AuthResponse>("/session/login", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  registerWithGoogle: (payload: GoogleRegisterPayload) =>
    request<AuthResponse>("/session/google-register", {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  loginWithGoogle: (idToken: string) =>
    request<AuthResponse>("/session/google-login", {
      method: "POST",
      body: JSON.stringify({ idToken }),
    }),

  forgotPassword: (email: string) =>
    request<void>("/api/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  resetPassword: (token: string, newPassword: string) =>
    request<void>("/api/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ token, newPassword }),
    }),

  changePassword: (token: string, currentPassword: string, newPassword: string) =>
    request<void>(
      "/api/auth/change-password",
      { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) },
      token
    ),

  logout: () => request<void>("/session/logout", { method: "POST" }),

  createStaff: (token: string, payload: CreateStaffPayload) =>
    request<UserSummary>("/api/users", { method: "POST", body: JSON.stringify(payload) }, token),

  updateUserStatus: (token: string, userId: string, active: boolean) =>
    request<UserSummary>(
      `/api/users/${userId}/status`,
      { method: "PATCH", body: JSON.stringify({ active }) },
      token
    ),

  updateUserRole: (token: string, userId: string, role: StaffRole) =>
    request<UserSummary>(
      `/api/users/${userId}/role`,
      { method: "PATCH", body: JSON.stringify({ role }) },
      token
    ),

  updateCommissionRate: (token: string, userId: string, rate: number) =>
    request<UserSummary>(
      `/api/users/${userId}/commission-rate`,
      { method: "PATCH", body: JSON.stringify({ rate }) },
      token
    ),

  getMyBusiness: (token: string) =>
    request<BusinessSummary>("/api/business/me", {}, token),

  updateBusinessProfile: (token: string, payload: BusinessUpdatePayload) =>
    request<BusinessSummary>(
      "/api/business/me",
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  updateBusinessSlug: (token: string, slug: string) =>
    request<BusinessSummary>(
      "/api/business/me/slug",
      { method: "PATCH", body: JSON.stringify({ slug }) },
      token
    ),

  uploadBusinessLogo: async (_token: string, file: File): Promise<BusinessSummary> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch("/api/business/logo", {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Upload failed");
    }
    return res.json();
  },

  listUsers: (token: string) => request<UserSummary[]>("/api/users", {}, token),

  listProducts: (token: string, filters?: { search?: string; categoryId?: string; includeArchived?: boolean }) => {
    const params = new URLSearchParams();
    if (filters?.search) params.set("search", filters.search);
    if (filters?.categoryId) params.set("categoryId", filters.categoryId);
    if (filters?.includeArchived) params.set("includeArchived", "true");
    const qs = params.toString();
    return request<Product[]>(`/api/products${qs ? `?${qs}` : ""}`, {}, token);
  },

  listLowStockProducts: (token: string) =>
    request<Product[]>("/api/products/low-stock", {}, token),

  createProduct: (token: string, payload: ProductPayload) =>
    request<Product>(
      "/api/products",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  updateProduct: (token: string, id: string, payload: ProductPayload) =>
    request<Product>(
      `/api/products/${id}`,
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  archiveProduct: (token: string, id: string) =>
    request<Product>(`/api/products/${id}/archive`, { method: "PATCH" }, token),

  restoreProduct: (token: string, id: string) =>
    request<Product>(`/api/products/${id}/restore`, { method: "PATCH" }, token),

  adjustStock: (token: string, id: string, payload: StockAdjustmentPayload) =>
    request<Product>(
      `/api/products/${id}/stock`,
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  stockHistory: (token: string, id: string) =>
    request<StockMovement[]>(`/api/products/${id}/stock-history`, {}, token),

  uploadProductPhoto: async (_token: string, id: string, file: File): Promise<Product> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch(`/api/products/${id}/photo`, {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Upload failed");
    }
    return res.json();
  },

  listProductCategories: (token: string) => request<ProductCategory[]>("/api/product-categories", {}, token),

  createProductCategory: (token: string, payload: ProductCategoryPayload) =>
    request<ProductCategory>("/api/product-categories", { method: "POST", body: JSON.stringify(payload) }, token),

  renameProductCategory: (token: string, id: string, payload: ProductCategoryPayload) =>
    request<ProductCategory>(`/api/product-categories/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  deleteProductCategory: (token: string, id: string) =>
    request<void>(`/api/product-categories/${id}`, { method: "DELETE" }, token),

  listServiceOrders: (token: string, filters?: { serviceTypeId?: string; status?: ServiceOrderStatus }) => {
    const params = new URLSearchParams();
    if (filters?.serviceTypeId) params.set("serviceTypeId", filters.serviceTypeId);
    if (filters?.status) params.set("status", filters.status);
    const qs = params.toString();
    return request<ServiceOrder[]>(`/api/service-orders${qs ? `?${qs}` : ""}`, {}, token);
  },

  listServiceTypes: (token: string) => request<ServiceType[]>("/api/service-types", {}, token),

  createServiceType: (token: string, payload: ServiceTypePayload) =>
    request<ServiceType>("/api/service-types", { method: "POST", body: JSON.stringify(payload) }, token),

  renameServiceType: (token: string, id: string, payload: ServiceTypePayload) =>
    request<ServiceType>(`/api/service-types/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  deleteServiceType: (token: string, id: string) =>
    request<void>(`/api/service-types/${id}`, { method: "DELETE" }, token),

  getServiceOrder: (token: string, id: string) => request<ServiceOrder>(`/api/service-orders/${id}`, {}, token),

  createServiceOrder: (token: string, payload: ServiceOrderPayload) =>
    request<ServiceOrder>("/api/service-orders", { method: "POST", body: JSON.stringify(payload) }, token),

  updateServiceOrder: (token: string, id: string, payload: ServiceOrderUpdatePayload) =>
    request<ServiceOrder>(`/api/service-orders/${id}`, { method: "PATCH", body: JSON.stringify(payload) }, token),

  updateServiceOrderStatus: (token: string, id: string, status: ServiceOrderStatus) =>
    request<ServiceOrder>(`/api/service-orders/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }, token),

  resendServiceOrderReadyEmail: (token: string, id: string) =>
    request<ServiceOrder>(`/api/service-orders/${id}/resend-ready-email`, { method: "POST" }, token),

  listServiceOrderPhotos: (token: string, orderId: string) =>
    request<ServiceOrderPhoto[]>(`/api/service-orders/${orderId}/photos`, {}, token),

  uploadServiceOrderPhoto: async (_token: string, orderId: string, file: File): Promise<ServiceOrderPhoto> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch(`/api/service-orders/${orderId}/photos`, {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Upload failed");
    }
    return res.json();
  },

  deleteServiceOrderPhoto: (token: string, orderId: string, photoId: string) =>
    request<void>(`/api/service-orders/${orderId}/photos/${photoId}`, { method: "DELETE" }, token),

  getServiceOrderReport: (token: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<ServiceOrderReport>(`/api/service-orders/report${qs ? `?${qs}` : ""}`, {}, token);
  },

  listServiceCatalog: (token: string, activeOnly?: boolean) =>
    request<ServiceCatalogItem[]>(`/api/service-catalog${activeOnly ? "?activeOnly=true" : ""}`, {}, token),

  createServiceCatalogItem: (token: string, payload: ServiceCatalogItemPayload) =>
    request<ServiceCatalogItem>("/api/service-catalog", { method: "POST", body: JSON.stringify(payload) }, token),

  updateServiceCatalogItem: (token: string, id: string, payload: ServiceCatalogItemPayload) =>
    request<ServiceCatalogItem>(`/api/service-catalog/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  setServiceCatalogItemActive: (token: string, id: string, active: boolean) =>
    request<ServiceCatalogItem>(`/api/service-catalog/${id}/active`, { method: "PATCH", body: JSON.stringify({ active }) }, token),

  listCustomers: (token: string) => request<Customer[]>("/api/customers", {}, token),

  getCustomer: (token: string, id: string) => request<Customer>(`/api/customers/${id}`, {}, token),

  createCustomer: (token: string, payload: CustomerPayload) =>
    request<Customer>(
      "/api/customers",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  listSales: (token: string) => request<Sale[]>("/api/sales", {}, token),

  getSale: (token: string, id: string) => request<Sale>(`/api/sales/${id}`, {}, token),

  createSale: (token: string, payload: SalePayload) =>
    request<Sale>("/api/sales", { method: "POST", body: JSON.stringify(payload) }, token),

  listExpenses: (token: string) => request<Expense[]>("/api/expenses", {}, token),

  createExpense: (token: string, payload: ExpensePayload) =>
    request<Expense>(
      "/api/expenses",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  getReportSummary: (token: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<ReportSummary>(`/api/reports/summary${qs ? `?${qs}` : ""}`, {}, token);
  },

  getCommissions: (token: string, from?: string, to?: string) => {
    const params = new URLSearchParams();
    if (from) params.set("from", from);
    if (to) params.set("to", to);
    const qs = params.toString();
    return request<StaffCommission[]>(`/api/reports/commissions${qs ? `?${qs}` : ""}`, {}, token);
  },

  downloadReportExport: async (token: string, from: string, to: string) => {
    await downloadFile(`/api/reports/export?from=${from}&to=${to}`, token, `report-${from}-to-${to}.xlsx`);
  },

  downloadReceipt: async (token: string, saleId: string, saleNumber: number) => {
    await downloadFile(`/api/sales/${saleId}/receipt`, token, `receipt-${saleNumber}.pdf`, true);
  },

  listSuppliers: (token: string) => request<Supplier[]>("/api/suppliers", {}, token),

  createSupplier: (token: string, payload: SupplierPayload) =>
    request<Supplier>("/api/suppliers", { method: "POST", body: JSON.stringify(payload) }, token),

  listPurchaseOrders: (token: string) => request<PurchaseOrder[]>("/api/purchase-orders", {}, token),

  createPurchaseOrder: (token: string, payload: PurchaseOrderPayload) =>
    request<PurchaseOrder>(
      "/api/purchase-orders",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  receivePurchaseOrder: (token: string, id: string) =>
    request<PurchaseOrder>(`/api/purchase-orders/${id}/receive`, { method: "POST" }, token),

  cancelPurchaseOrder: (token: string, id: string) =>
    request<PurchaseOrder>(`/api/purchase-orders/${id}/cancel`, { method: "POST" }, token),

  listActivityLogs: (token: string, filters?: { userId?: string; from?: string; to?: string }) => {
    const params = new URLSearchParams();
    if (filters?.userId) params.set("userId", filters.userId);
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<ActivityLogEntry[]>(`/api/activity-logs${qs ? `?${qs}` : ""}`, {}, token);
  },

  // --- Billing (Owner only) ---

  getBillingStatus: (token: string) => request<BillingStatus>("/api/billing/status", {}, token),

  listBillingPlans: (token: string) => request<SubscriptionPlan[]>("/api/billing/plans", {}, token),

  getBillingHistory: (token: string) => request<SubscriptionPaymentSummary[]>("/api/billing/history", {}, token),

  startBillingCheckout: (token: string, planId: string) =>
    request<CheckoutResponse>("/api/billing/checkout", { method: "POST", body: JSON.stringify({ planId }) }, token),

  verifyBillingPayment: (token: string, reference: string) =>
    request<VerifyPaymentResponse>("/api/billing/verify", { method: "POST", body: JSON.stringify({ reference }) }, token),

  // --- Business Integrations (Owner only — client's own Paystack/WooCommerce keys) ---

  getBusinessIntegrations: (token: string) => request<BusinessIntegrations>("/api/integrations", {}, token),

  updateBusinessIntegrations: (token: string, payload: BusinessIntegrationsPayload) =>
    request<BusinessIntegrations>("/api/integrations", { method: "PUT", body: JSON.stringify(payload) }, token),

  testPaystackIntegration: (token: string) =>
    request<TestConnectionResult>("/api/integrations/test-paystack", { method: "POST" }, token),

  testWooCommerceIntegration: (token: string) =>
    request<TestConnectionResult>("/api/integrations/test-woocommerce", { method: "POST" }, token),

  // --- Bookings (Owner sees/edits settings; STAFF sees their own assigned bookings) ---

  listBookings: (token: string, status?: string) =>
    request<BookingListItem[]>(`/api/bookings${status ? `?status=${status}` : ""}`, {}, token),

  // Staff creating a booking on a customer's behalf (e.g. a phone-in request).
  createStaffBooking: (token: string, payload: CreateStaffBookingPayload) =>
    request<BookingCreated>("/api/bookings", { method: "POST", body: JSON.stringify(payload) }, token),

  getBookingSettings: (token: string) => request<BookingSettings>("/api/bookings/settings", {}, token),

  updateBookingSettings: (token: string, payload: BookingSettingsPayload) =>
    request<BookingSettings>("/api/bookings/settings", { method: "PUT", body: JSON.stringify(payload) }, token),

  listBlackoutDates: (token: string) => request<BlackoutDate[]>("/api/bookings/blackout-dates", {}, token),

  addBlackoutDate: (token: string, payload: { date: string; label?: string }) =>
    request<BlackoutDate>("/api/bookings/blackout-dates", { method: "POST", body: JSON.stringify(payload) }, token),

  removeBlackoutDate: (token: string, id: string) =>
    request<void>(`/api/bookings/blackout-dates/${id}`, { method: "DELETE" }, token),

  // --- Platform (Super Admin) ---

  platformLogin: (email: string, password: string) =>
    request<PlatformAuthResponse>("/session/platform-login", {
      method: "POST",
      body: JSON.stringify({ email, password }),
    }),

  platformLogout: () => request<void>("/session/platform-logout", { method: "POST" }),

  platformForgotPassword: (email: string) =>
    request<void>("/api/platform/auth/forgot-password", {
      method: "POST",
      body: JSON.stringify({ email }),
    }),

  platformResetPassword: (token: string, newPassword: string) =>
    request<void>("/api/platform/auth/reset-password", {
      method: "POST",
      body: JSON.stringify({ token, newPassword }),
    }),

  platformChangePassword: (token: string, currentPassword: string, newPassword: string) =>
    request<void>(
      "/api/platform/auth/change-password",
      { method: "POST", body: JSON.stringify({ currentPassword, newPassword }) },
      token
    ),

  listPlatformAdmins: (token: string) => request<PlatformAdminSummary[]>("/api/platform/admins", {}, token),

  createPlatformAdmin: (token: string, payload: CreatePlatformAdminPayload) =>
    request<PlatformAdminSummary>(
      "/api/platform/admins",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  listPlatformBusinesses: (token: string, query?: string, active?: boolean) => {
    const params = new URLSearchParams();
    if (query) params.set("query", query);
    if (active !== undefined) params.set("active", String(active));
    const qs = params.toString();
    return request<PlatformBusinessSummary[]>(`/api/platform/businesses${qs ? `?${qs}` : ""}`, {}, token);
  },

  getPlatformBusiness: (token: string, id: string) =>
    request<PlatformBusinessDetail>(`/api/platform/businesses/${id}`, {}, token),

  setPlatformBusinessStatus: (token: string, id: string, active: boolean) =>
    request<PlatformBusinessSummary>(
      `/api/platform/businesses/${id}/status`,
      { method: "PATCH", body: JSON.stringify({ active }) },
      token
    ),

  updatePlatformBusinessBilling: (token: string, id: string, payload: PlatformBusinessBillingUpdatePayload) =>
    request<PlatformBusinessDetail>(
      `/api/platform/businesses/${id}/billing`,
      { method: "PATCH", body: JSON.stringify(payload) },
      token
    ),

  deletePlatformBusiness: (token: string, id: string) =>
    request<void>(`/api/platform/businesses/${id}`, { method: "DELETE" }, token),

  resetPlatformUserPassword: (token: string, businessId: string, userId: string) =>
    request<AdminResetPasswordResponse>(
      `/api/platform/businesses/${businessId}/users/${userId}/reset-password`,
      { method: "POST" },
      token
    ),

  listPlatformActivityLogs: (
    token: string,
    filters?: { businessId?: string; userId?: string; from?: string; to?: string }
  ) => {
    const params = new URLSearchParams();
    if (filters?.businessId) params.set("businessId", filters.businessId);
    if (filters?.userId) params.set("userId", filters.userId);
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<ActivityLogEntry[]>(`/api/platform/activity-logs${qs ? `?${qs}` : ""}`, {}, token);
  },

  listPlatformAuditLogs: (token: string) =>
    request<PlatformAuditLogEntry[]>("/api/platform/audit-logs", {}, token),

  getPlatformStats: (token: string) => request<PlatformStats>("/api/platform/stats", {}, token),

  listPlatformSubscriptionPlans: (token: string) =>
    request<SubscriptionPlan[]>("/api/platform/subscription-plans", {}, token),

  createPlatformSubscriptionPlan: (token: string, payload: SubscriptionPlanPayload) =>
    request<SubscriptionPlan>(
      "/api/platform/subscription-plans",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  updatePlatformSubscriptionPlan: (token: string, id: string, payload: SubscriptionPlanPayload) =>
    request<SubscriptionPlan>(
      `/api/platform/subscription-plans/${id}`,
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  archivePlatformSubscriptionPlan: (token: string, id: string) =>
    request<SubscriptionPlan>(`/api/platform/subscription-plans/${id}/archive`, { method: "PATCH" }, token),

  restorePlatformSubscriptionPlan: (token: string, id: string) =>
    request<SubscriptionPlan>(`/api/platform/subscription-plans/${id}/restore`, { method: "PATCH" }, token),

  getPlatformBillingSettings: (token: string) =>
    request<PlatformBillingSettings>("/api/platform/billing-settings", {}, token),

  updatePlatformBillingSettings: (token: string, payload: PlatformBillingSettingsPayload) =>
    request<PlatformBillingSettings>(
      "/api/platform/billing-settings",
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  // Public booking self-service — reached via a manage_token from the
  // confirmation email, no session/cookie involved.
  getBookingByToken: (manageToken: string) =>
    request<BookingDetail>(`/api/public/bookings/${manageToken}`),

  rescheduleBooking: (manageToken: string, scheduledAt: string) =>
    request<void>(`/api/public/bookings/${manageToken}/reschedule`, {
      method: "PATCH",
      body: JSON.stringify({ scheduledAt }),
    }),

  cancelBooking: (manageToken: string) =>
    request<void>(`/api/public/bookings/${manageToken}`, { method: "DELETE" }),

  startBookingPayment: (manageToken: string) =>
    request<CheckoutResponse>(`/api/public/bookings/${manageToken}/pay`, { method: "POST" }),

  verifyBookingPayment: (reference: string) =>
    request<{ success: boolean; message: string }>("/api/public/bookings/verify", {
      method: "POST",
      body: JSON.stringify({ reference }),
    }),

  payInPersonBooking: (manageToken: string) =>
    request<void>(`/api/public/bookings/${manageToken}/pay-in-person`, { method: "POST" }),

  // Owner-side package management (mirrors service-catalog CRUD shape).
  listServicePackages: (token: string) => request<ServicePackage[]>("/api/service-packages", {}, token),

  createServicePackage: (token: string, payload: ServicePackagePayload) =>
    request<ServicePackage>("/api/service-packages", { method: "POST", body: JSON.stringify(payload) }, token),

  updateServicePackage: (token: string, id: string, payload: ServicePackagePayload) =>
    request<ServicePackage>(`/api/service-packages/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  setServicePackageActive: (token: string, id: string, active: boolean) =>
    request<ServicePackage>(`/api/service-packages/${id}/active`, { method: "PATCH", body: JSON.stringify({ active }) }, token),

  // Hosted booking page (ratel.app/book/{slug}) — for businesses with no
  // website of their own to embed the widget on.
  getBookingWidgetConfigBySlug: (slug: string) =>
    request<BookingWidgetConfig>(`/api/public/bookings/by-slug/${slug}/widget-config`),

  listPublicBookableServices: (businessId: string) =>
    request<BookableService[]>(`/api/public/bookings/services?businessId=${businessId}`),

  createPublicBooking: (businessId: string, payload: CreateBookingPayload) =>
    request<BookingCreated>(`/api/public/bookings?businessId=${businessId}`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),

  // Hosted hub page (ratel.app/start/{slug}) — the one link a business puts
  // in its WhatsApp bio.
  getStartHubConfigBySlug: (slug: string) =>
    request<StartHubConfig>(`/api/public/start/by-slug/${slug}`),

  // Hosted custom-order page (ratel.app/order/{slug}) — for businesses with
  // no website of their own to embed custom-wig.js on.
  getCustomWigConfigBySlug: (slug: string) =>
    request<PublicCustomWigConfig>(`/api/public/custom-wig/by-slug/${slug}/config`),

  submitCustomWigRequest: async (
    businessId: string,
    payload: {
      customerName: string;
      customerEmail: string;
      customerWhatsapp: string;
      selections: CustomWigSelectionInput[];
      notes?: string;
    },
    photo: File | null
  ): Promise<CustomWigRequestCreated> => {
    const formData = new FormData();
    formData.append("payload", JSON.stringify(payload));
    if (photo) formData.append("photo", photo);
    const res = await fetch(`/api/public/custom-wig/requests?businessId=${businessId}`, {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Couldn't submit that request.");
    }
    return res.json();
  },

  listEcommerceOrders: (token: string) =>
    request<EcommerceOrder[]>("/api/ecommerce-orders", {}, token),

  getEcommerceOrder: (token: string, id: string) =>
    request<EcommerceOrderDetail>(`/api/ecommerce-orders/${id}`, {}, token),

  updateEcommerceOrderStatus: (token: string, id: string, status: EcommerceOrderStatus) =>
    request<EcommerceOrder>(
      `/api/ecommerce-orders/${id}/status`,
      { method: "PATCH", body: JSON.stringify({ status }) },
      token
    ),

  listCustomWigAttributes: (token: string) =>
    request<CustomItemAttribute[]>("/api/custom-wig-attributes", {}, token),

  createCustomWigAttribute: (token: string, payload: CustomItemAttributePayload) =>
    request<CustomItemAttribute>(
      "/api/custom-wig-attributes",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  updateCustomWigAttribute: (token: string, id: string, payload: CustomItemAttributePayload) =>
    request<CustomItemAttribute>(
      `/api/custom-wig-attributes/${id}`,
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  deleteCustomWigAttribute: (token: string, id: string) =>
    request<void>(`/api/custom-wig-attributes/${id}`, { method: "DELETE" }, token),

  listCustomWigRequests: (token: string) =>
    request<CustomWigRequest[]>("/api/custom-wig-requests", {}, token),

  getCustomWigRequest: (token: string, id: string) =>
    request<CustomWigRequestDetail>(`/api/custom-wig-requests/${id}`, {}, token),

  quoteCustomWigRequest: (token: string, id: string, finalPrice: number, message: string) =>
    request<CustomWigRequest>(
      `/api/custom-wig-requests/${id}/quote`,
      { method: "PATCH", body: JSON.stringify({ finalPrice, message }) },
      token
    ),

  declineCustomWigRequest: (token: string, id: string, message: string) =>
    request<CustomWigRequest>(
      `/api/custom-wig-requests/${id}/decline`,
      { method: "PATCH", body: JSON.stringify({ message }) },
      token
    ),

  acceptCustomWigRequest: (token: string, id: string) =>
    request<CustomWigRequest>(`/api/custom-wig-requests/${id}/accept`, { method: "PATCH" }, token),
};

export { ApiError };
