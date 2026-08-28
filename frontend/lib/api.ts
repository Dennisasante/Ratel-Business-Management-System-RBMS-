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
  signatureUrl: string | null;
  taxId: string | null;
  defaultTermsAndConditions: string | null;
  billingStatus: "TRIALING" | "ACTIVE" | "GRACE" | "READ_ONLY";
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

// A name to assign work to — no login, no role. Replaced STAFF-role User
// accounts; see StaffMemberController on the backend.
export interface StaffMember {
  id: string;
  fullName: string;
  phone: string | null;
  notes: string | null;
  active: boolean;
  createdAt: string;
}

export interface StaffMemberPayload {
  fullName: string;
  phone?: string;
  notes?: string;
}

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
  totalSubscriptionRevenue: number;
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

export interface ImportRow {
  rowNumber: number;
  name: string | null;
  category: string | null;
  sku: string | null;
  costPrice: number | null;
  sellingPrice: number | null;
  quantity: number | null;
  lowStockThreshold: number | null;
  supplierName: string | null;
  valid: boolean;
  errors: string[];
}

export interface ImportPreviewResponse {
  rows: ImportRow[];
  validCount: number;
  errorCount: number;
}

export interface ImportSkip {
  rowNumber: number;
  name: string | null;
  reason: string;
}

export interface ImportResultResponse {
  importedCount: number;
  skippedCount: number;
  skipped: ImportSkip[];
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
  // How this customer found the business — Walk-in/Instagram/WhatsApp/
  // Facebook/Referral/Website/Other, free text. Null for older customers.
  source: string | null;
  totalSpent: number;
  purchaseCount: number;
  createdAt: string;
}

export interface CustomerPayload {
  fullName: string;
  phone?: string;
  email?: string;
  notes?: string;
  source?: string;
}

export type PaymentMethod = "CASH" | "MOBILE_MONEY" | "MOBILE_MONEY_DIRECT";

export interface RecordPaymentPayload {
  amount: number;
  method: PaymentMethod;
  note?: string;
}

export type SaleItemType = "PRODUCT" | "SERVICE";

export interface SaleItemPayload {
  // Exactly one of these two must be set.
  productId?: string;
  serviceCatalogId?: string;
  quantity: number;
  discountAmount?: number;
  gift?: boolean;
}

export interface SalePayload {
  customerId?: string;
  paymentMethod: PaymentMethod;
  items: SaleItemPayload[];
}

export interface SaleItem {
  id: string;
  itemType: SaleItemType;
  productId: string | null;
  serviceCatalogId: string | null;
  productName: string;
  unitPrice: number;
  quantity: number;
  discountAmount: number;
  subtotal: number;
  gift: boolean;
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
  // UNPAID/PARTIALLY_PAID/PAID/FAILED/REFUNDED — CASH/MOBILE_MONEY_DIRECT
  // sales are PAID immediately; MOBILE_MONEY (Online Payment) starts UNPAID
  // until charged or manually marked paid.
  paymentStatus: string;
  amountPaid: number;
  // Derived server-side — totalAmount minus amountPaid, clamped to zero.
  balanceDue: number;
}

export type ExpenseCategory =
  | "RENT"
  | "UTILITIES"
  | "TRANSPORT"
  | "SUPPLIES"
  | "SALARY"
  | "MARKETING"
  | "OTHER";

export type ExpensePaymentMethod = "CASH" | "MOBILE_MONEY";

export interface Expense {
  id: string;
  category: ExpenseCategory;
  description: string | null;
  paymentMethod: ExpensePaymentMethod;
  amount: number;
  expenseDate: string;
  recordedByName: string;
  createdAt: string;
}

export interface ExpensePayload {
  category: ExpenseCategory;
  description?: string;
  paymentMethod: ExpensePaymentMethod;
  amount: number;
  expenseDate?: string;
}

export interface ExpenseEditPayload {
  expense: ExpensePayload;
  reason: string;
}

export type InvoiceStatus = "DRAFT" | "SENT" | "PAID" | "OVERDUE";

export interface InvoiceItem {
  id: string;
  description: string;
  quantity: number;
  unitPrice: number;
  discountAmount: number;
  subtotal: number;
}

export interface Invoice {
  id: string;
  invoiceNumber: number;
  customerId: string | null;
  customerName: string | null;
  customerEmail: string | null;
  customerPhone: string | null;
  customerAddress: string | null;
  issueDate: string;
  dueDate: string | null;
  notes: string | null;
  termsAndConditions: string | null;
  status: InvoiceStatus;
  subtotal: number;
  discountAmount: number;
  taxRate: number | null;
  taxAmount: number;
  shippingAmount: number;
  totalAmount: number;
  items: InvoiceItem[];
  createdAt: string;
}

export interface InvoiceSummary {
  id: string;
  invoiceNumber: number;
  customerName: string | null;
  issueDate: string;
  dueDate: string | null;
  status: InvoiceStatus;
  totalAmount: number;
}

export interface InvoiceItemPayload {
  description: string;
  quantity: number;
  unitPrice: number;
  discountAmount?: number;
}

export interface InvoicePayload {
  customerId?: string;
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  customerAddress?: string;
  issueDate: string;
  dueDate?: string;
  notes?: string;
  termsAndConditions?: string;
  taxRate?: number;
  shippingAmount?: number;
  items: InvoiceItemPayload[];
}

export interface AiSettings {
  active: boolean;
  agentName: string;
  greeting: string | null;
  tone: string | null;
  systemInstructions: string | null;
  humanHandoffEnabled: boolean;
  humanHandoffMessage: string | null;
}

export interface AiSettingsPayload {
  active: boolean;
  agentName: string;
  greeting?: string;
  tone?: string;
  systemInstructions?: string;
  humanHandoffEnabled: boolean;
  humanHandoffMessage?: string;
}

export interface AiOverview {
  active: boolean;
  agentName: string;
  conversationCount: number;
  activeConversationCount: number;
  escalatedCount: number;
  actionCount: number;
  knowledgeEntryCount: number;
  bookingsCreatedByAi: number;
}

// Suggested values: FAQ, BUSINESS_INFO, SERVICE, POLICY, RESTAURANT, HOTEL,
// EVENTS, BEACH, OTHER — plain string, matching the backend's own
// not-a-native-enum convention.
export type AiKnowledgeCategory = string;

export interface AiKnowledgeEntry {
  id: string;
  title: string;
  content: string;
  category: AiKnowledgeCategory;
  active: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AiKnowledgeEntryPayload {
  title: string;
  content: string;
  category: AiKnowledgeCategory;
  active: boolean;
}

export type AiConversationChannel = "WEB_DEMO" | "WHATSAPP" | "INSTAGRAM" | "FACEBOOK" | "PHONE" | "SMS" | "EMAIL";
export type AiConversationStatus = "ACTIVE" | "ESCALATED" | "CLOSED";

export interface AiConversationSummary {
  id: string;
  customerId: string | null;
  customerName: string | null;
  channel: AiConversationChannel;
  status: AiConversationStatus;
  startedAt: string;
  lastMessageAt: string;
}

export interface AiMessage {
  id: string;
  role: "USER" | "ASSISTANT" | "SYSTEM" | "TOOL";
  content: string;
  createdAt: string;
}

export interface AiActionEntry {
  toolName: string;
  status: "STARTED" | "SUCCEEDED" | "FAILED" | "BLOCKED";
  createdAt: string;
}

export interface AiConversationDetail extends AiConversationSummary {
  messages: AiMessage[];
  actions: AiActionEntry[];
}

export interface AiToolCallSummary {
  toolName: string;
  status: "SUCCEEDED" | "FAILED" | "BLOCKED";
  summary: string;
}

export interface AiChatResponse {
  conversationId: string;
  assistantMessage: string;
  conversationStatus: AiConversationStatus;
  toolCalls: AiToolCallSummary[];
}

export interface AiChannelStatus {
  channel: AiConversationChannel;
  label: string;
  connected: boolean;
  statusMessage: string;
  active: boolean;
  phoneNumberId: string | null;
  displayName: string | null;
  updatedAt: string | null;
}

// ---- Tallia AI: WhatsApp channel binding (Super Admin only, Phase 3B) ----

export interface WhatsAppBinding {
  id: string;
  businessId: string;
  businessName: string;
  whatsappBusinessAccountId: string | null;
  phoneNumberId: string;
  displayName: string | null;
  active: boolean;
  // True once an access token has been saved at least once — never says
  // whether that token still actually works, see WhatsAppConnectionTest.
  configured: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WhatsAppBindingCreatePayload {
  whatsappBusinessAccountId?: string;
  phoneNumberId: string;
  displayName?: string;
  // Write-only — never returned by any endpoint afterward.
  accessToken: string;
  active: boolean;
}

export interface WhatsAppBindingUpdatePayload {
  whatsappBusinessAccountId?: string;
  phoneNumberId?: string;
  displayName?: string;
  // Omit entirely to leave the stored token untouched.
  accessToken?: string;
  active?: boolean;
}

export interface WhatsAppConnectionTestResult {
  success: boolean;
  displayPhoneNumber: string | null;
  verifiedName: string | null;
  errorMessage: string | null;
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
  // UNPAID/PAID — whether the business has settled this PO with the supplier.
  paymentStatus: string;
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

export interface ServiceOrderItemResponse {
  id: string;
  serviceTypeId: string;
  serviceTypeName: string | null;
  serviceCatalogId: string | null;
  serviceName: string;
  price: number;
  discountAmount: number;
}

export interface ServiceOrder {
  id: string;
  orderNumber: number;
  serviceTypeId: string;
  serviceTypeName: string | null;
  status: ServiceOrderStatus;
  customerId: string | null;
  customerName: string | null;
  customerPhone: string | null;
  serviceCatalogId: string | null;
  serviceCatalogName: string | null;
  notes: string | null;
  price: number;
  discountAmount: number;
  // Every service on this order, individually — serviceTypeId/serviceCatalogId/
  // price/discountAmount above stay as the "primary type" (first item's) and
  // running totals for backward compatibility; this is the source of truth
  // once an order has more than one.
  items: ServiceOrderItemResponse[];
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
  customerWhatsappLink: string | null;
  paymentStatus: string;
  amountPaid: number;
  // Derived server-side — price minus amountPaid, clamped to zero.
  balanceDue: number;
}

export interface ServiceOrderItemPayload {
  serviceTypeId: string;
  serviceCatalogId?: string;
  // A typed description (e.g. "6 inches HD bone straight wig, middle part")
  // for a line with no catalog item — takes precedence over the catalog/
  // category name when present.
  customName?: string;
  price: number;
  discountAmount?: number;
}

export interface ServiceOrderPayload {
  customerId?: string;
  notes?: string;
  items: ServiceOrderItemPayload[];
  assignedStaffId?: string;
  scheduledAt?: string;
}

export interface ServiceOrderUpdatePayload {
  notes?: string;
  price?: number;
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
  revenueByService: {
    serviceCatalogId: string | null;
    serviceName: string;
    serviceTypeId: string | null;
    serviceTypeName: string | null;
    revenue: number;
  }[];
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
  taxId?: string;
  defaultTermsAndConditions?: string;
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
  billingStatus: "TRIALING" | "ACTIVE" | "GRACE" | "READ_ONLY";
  plan: SubscriptionPlan | null;
  trialEndsAt: string | null;
  currentPeriodEndsAt: string | null;
  // Only meaningful (non-null) while billingStatus === "GRACE".
  gracePeriodEndsAt: string | null;
  daysRemaining: number;
  // Null when no card is saved.
  cardLast4: string | null;
  cardBrand: string | null;
  autoRenewEnabled: boolean;
  usdDisplayRate: number | null;
  // This business's real monthly rate — plan.price unless a Super Admin has
  // set a custom rate. Multi-month discounts apply on top of this, not on
  // top of plan.price, whenever the two differ.
  effectiveMonthlyRate: number | null;
}

export interface CheckoutResponse {
  accessCode: string;
  reference: string;
}

export interface PaymentTransaction {
  id: string;
  direction: "INCOMING" | "OUTGOING";
  sourceType: "SERVICE_ORDER" | "SALE" | "BOOKING" | "PURCHASE_ORDER";
  sourceId: string | null;
  sourceLabel: string | null;
  gateway: string; // "PAYSTACK" | "MANUAL"
  method: string | null;
  amount: number;
  currency: string;
  status: string; // PENDING / SUCCESS / FAILED
  gatewayReference: string | null;
  customerId: string | null;
  customerName: string | null;
  customerPhone: string | null;
  note: string | null;
  createdByName: string | null;
  paidAt: string | null;
  createdAt: string;
}

// Lightweight rows for the Super Admin per-business data-cleanup panel.
export interface PlatformServiceOrderSummary {
  id: string;
  orderNumber: number;
  customerName: string;
  price: number;
  status: string;
  paymentStatus: string;
  receivedAt: string;
}

export interface PlatformSaleSummary {
  id: string;
  saleNumber: number;
  customerName: string;
  totalAmount: number;
  paymentStatus: string;
  createdAt: string;
}

export interface PlatformCustomerSummary {
  id: string;
  fullName: string;
  phone: string;
  email: string | null;
  createdAt: string;
}

export interface MobileMoneyChargeResponse {
  reference: string;
  status: string;
  displayText: string | null;
  // Paystack's own explanation of the outcome — most important on an outright
  // rejection ("failed" status), where it's the only clue as to why nothing
  // was sent to the customer's phone (e.g. a first-time payer needing
  // identification on their mobile money account).
  message: string | null;
}

export type MobileMoneyProvider = "mtn" | "atl" | "vod";

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
  notifyOnSale: boolean;
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
  notifyOnSale?: boolean;
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
  id: string;
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
  arrivedAt: string | null;
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

export type CustomWigRequestStatus = "SUBMITTED" | "QUOTED" | "ACCEPTED" | "IN_PROGRESS" | "COMPLETED" | "PICKED_UP" | "DECLINED";

export interface CustomWigRequest {
  id: string;
  requestNumber: number;
  customerName: string;
  customerEmail: string | null;
  customerWhatsapp: string | null;
  description: string | null;
  estimatedPrice: number;
  status: CustomWigRequestStatus;
  finalPrice: number | null;
  // UNPAID/PARTIALLY_PAID/PAID/FAILED/REFUNDED — same model as Sale/ServiceOrder.
  paymentStatus: string;
  amountPaid: number;
  balanceDue: number | null;
  whatsappLink: string | null;
  source: string | null;
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

// Always free text + a staff-entered price — never the public widget's
// attribute/option picker. See CreateStaffCustomWigRequestRequest.java.
export interface CreateStaffCustomWigRequestPayload {
  customerName: string;
  customerEmail?: string;
  customerWhatsapp?: string;
  source?: string;
  description: string;
  price: number;
  notes?: string;
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
  customerEmail: string | null;
  customerWhatsapp: string | null;
  selections: CustomWigSelection[];
  description: string | null;
  estimatedPrice: number;
  inspirationPhotoUrl: string | null;
  notes: string | null;
  status: CustomWigRequestStatus;
  finalPrice: number | null;
  ownerMessage: string | null;
  paymentStatus: string;
  amountPaid: number;
  balanceDue: number | null;
  paymentMethod: PaymentMethod | null;
  whatsappLink: string | null;
  source: string | null;
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
  months: number;
  status: "PENDING" | "SUCCESS" | "FAILED";
  periodStart: string | null;
  periodEnd: string | null;
  paidAt: string | null;
  createdAt: string;
}

export type PlatformBillingStatus = "TRIALING" | "ACTIVE" | "GRACE" | "READ_ONLY";

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

export type HelpRequestCategory = "GENERAL" | "BUG" | "BILLING" | "FEATURE_REQUEST";
export type HelpRequestStatus = "OPEN" | "RESOLVED";

export interface HelpRequest {
  id: string;
  requesterName: string;
  requesterEmail: string;
  category: HelpRequestCategory;
  subject: string;
  message: string;
  status: HelpRequestStatus;
  adminResponse: string | null;
  respondedAt: string | null;
  createdAt: string;
}

export interface HelpRequestPayload {
  category: HelpRequestCategory;
  subject: string;
  message: string;
}

export interface PlatformHelpRequest extends HelpRequest {
  businessId: string;
  businessName: string;
}

export interface Notification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  sourceType: string | null;
  sourceId: string | null;
  read: boolean;
  createdAt: string;
}

export interface PendingApproval {
  id: string;
  sourceType: "SALE" | "SERVICE_ORDER" | "CUSTOM_WIG_REQUEST";
  sourceId: string;
  actionType: "EDIT_PRICE" | "REFUND";
  summary: string;
  status: "PENDING" | "APPROVED" | "REJECTED";
  requestedByName: string;
  requestedAt: string;
}

// Shape of the 202 response a non-Owner's price edit/refund gets back instead
// of the updated record — see ApprovalGateService/GlobalExceptionHandler on
// the backend. Call site pattern: check isPendingApproval(result) before
// treating the response as the updated Sale/ServiceOrder/CustomWigRequest.
export interface PendingApprovalOutcome {
  status: "PENDING_APPROVAL";
  pendingApprovalId: string;
  message: string;
}

export function isPendingApproval(result: unknown): result is PendingApprovalOutcome {
  return !!result && typeof result === "object" && (result as { status?: string }).status === "PENDING_APPROVAL";
}

export interface OnboardingStatus {
  completed: boolean;
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

  uploadBusinessSignature: async (_token: string, file: File): Promise<BusinessSummary> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch("/api/business/signature", {
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

  listStaffMembers: (token: string) => request<StaffMember[]>("/api/staff-members", {}, token),

  createStaffMember: (token: string, payload: StaffMemberPayload) =>
    request<StaffMember>("/api/staff-members", { method: "POST", body: JSON.stringify(payload) }, token),

  updateStaffMember: (token: string, id: string, payload: StaffMemberPayload) =>
    request<StaffMember>(`/api/staff-members/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  setStaffMemberActive: (token: string, id: string, active: boolean) =>
    request<StaffMember>(`/api/staff-members/${id}/status`, { method: "PATCH", body: JSON.stringify({ active }) }, token),

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

  downloadImportTemplate: async (token: string) => {
    await downloadFile("/api/products/import/template", token, "inventory-import-template.csv");
  },

  previewProductImport: async (_token: string, file: File): Promise<ImportPreviewResponse> => {
    const formData = new FormData();
    formData.append("file", file);
    const res = await fetch("/api/products/import/preview", {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Couldn't read that file.");
    }
    return res.json();
  },

  confirmProductImport: (token: string, rows: ImportRow[]) =>
    request<ImportResultResponse>(
      "/api/products/import/confirm",
      { method: "POST", body: JSON.stringify({ rows }) },
      token
    ),

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
    request<ServiceOrder | PendingApprovalOutcome>(`/api/service-orders/${id}`, { method: "PATCH", body: JSON.stringify(payload) }, token),

  updateServiceOrderStatus: (token: string, id: string, status: ServiceOrderStatus) =>
    request<ServiceOrder>(`/api/service-orders/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }, token),

  resendServiceOrderReadyEmail: (token: string, id: string) =>
    request<ServiceOrder>(`/api/service-orders/${id}/resend-ready-email`, { method: "POST" }, token),

  chargeServiceOrderMobileMoney: (token: string, id: string, phone: string, provider: MobileMoneyProvider) =>
    request<MobileMoneyChargeResponse>(
      `/api/service-orders/${id}/charge-mobile-money`,
      { method: "POST", body: JSON.stringify({ phone, provider }) },
      token
    ),

  verifyServiceOrderPayment: (token: string, reference: string) =>
    request<ServiceOrder>("/api/service-orders/verify", { method: "POST", body: JSON.stringify({ reference }) }, token),

  submitServiceOrderMobileMoneyOtp: (token: string, reference: string, otp: string) =>
    request<ServiceOrder>(
      "/api/service-orders/submit-mobile-money-otp",
      { method: "POST", body: JSON.stringify({ reference, otp }) },
      token
    ),

  markServiceOrderPaid: (token: string, id: string) =>
    request<ServiceOrder>(`/api/service-orders/${id}/mark-paid`, { method: "POST" }, token),

  recordServiceOrderPayment: (token: string, id: string, payload: RecordPaymentPayload) =>
    request<ServiceOrder>(`/api/service-orders/${id}/record-payment`, { method: "POST", body: JSON.stringify(payload) }, token),

  refundServiceOrder: (token: string, id: string, note?: string) =>
    request<ServiceOrder | PendingApprovalOutcome>(`/api/service-orders/${id}/refund`, { method: "POST", body: JSON.stringify({ note }) }, token),

  listPaymentTransactions: (
    token: string,
    filters?: { direction?: "INCOMING" | "OUTGOING"; gateway?: string; from?: string; to?: string }
  ) => {
    const params = new URLSearchParams();
    if (filters?.direction) params.set("direction", filters.direction);
    if (filters?.gateway) params.set("gateway", filters.gateway);
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<PaymentTransaction[]>(`/api/payment-transactions${qs ? `?${qs}` : ""}`, {}, token);
  },

  verifyPaymentTransaction: (token: string, id: string) =>
    request<void>(`/api/payment-transactions/${id}/verify`, { method: "POST" }, token),

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

  listCustomers: (token: string, filters?: { search?: string }) => {
    const params = new URLSearchParams();
    if (filters?.search) params.set("search", filters.search);
    const qs = params.toString();
    return request<Customer[]>(`/api/customers${qs ? `?${qs}` : ""}`, {}, token);
  },

  getCustomer: (token: string, id: string) => request<Customer>(`/api/customers/${id}`, {}, token),

  createCustomer: (token: string, payload: CustomerPayload) =>
    request<Customer>(
      "/api/customers",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  listSales: (token: string, filters?: { from?: string; to?: string }) => {
    const params = new URLSearchParams();
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<Sale[]>(`/api/sales${qs ? `?${qs}` : ""}`, {}, token);
  },

  getSale: (token: string, id: string) => request<Sale>(`/api/sales/${id}`, {}, token),

  createSale: (token: string, payload: SalePayload) =>
    request<Sale>("/api/sales", { method: "POST", body: JSON.stringify(payload) }, token),

  chargeSaleMobileMoney: (token: string, id: string, phone: string, provider: MobileMoneyProvider) =>
    request<MobileMoneyChargeResponse>(
      `/api/sales/${id}/charge-mobile-money`,
      { method: "POST", body: JSON.stringify({ phone, provider }) },
      token
    ),

  verifySalePayment: (token: string, reference: string) =>
    request<Sale>("/api/sales/verify", { method: "POST", body: JSON.stringify({ reference }) }, token),

  submitSaleMobileMoneyOtp: (token: string, reference: string, otp: string) =>
    request<Sale>("/api/sales/submit-mobile-money-otp", { method: "POST", body: JSON.stringify({ reference, otp }) }, token),

  markSalePaid: (token: string, id: string) =>
    request<Sale>(`/api/sales/${id}/mark-paid`, { method: "POST" }, token),

  recordSalePayment: (token: string, id: string, payload: RecordPaymentPayload) =>
    request<Sale>(`/api/sales/${id}/record-payment`, { method: "POST", body: JSON.stringify(payload) }, token),

  refundSale: (token: string, id: string, note?: string) =>
    request<Sale | PendingApprovalOutcome>(`/api/sales/${id}/refund`, { method: "POST", body: JSON.stringify({ note }) }, token),

  updateSaleItemPrice: (
    token: string,
    saleId: string,
    itemId: string,
    payload: { unitPrice: number; discountAmount?: number; note?: string }
  ) =>
    request<Sale | PendingApprovalOutcome>(
      `/api/sales/${saleId}/items/${itemId}/price`,
      { method: "PATCH", body: JSON.stringify(payload) },
      token
    ),

  listExpenses: (token: string) => request<Expense[]>("/api/expenses", {}, token),

  createExpense: (token: string, payload: ExpensePayload) =>
    request<Expense>(
      "/api/expenses",
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  updateExpense: (token: string, id: string, payload: ExpenseEditPayload) =>
    request<Expense | PendingApprovalOutcome>(
      `/api/expenses/${id}`,
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  listInvoices: (token: string) => request<InvoiceSummary[]>("/api/invoices", {}, token),

  getInvoice: (token: string, id: string) => request<Invoice>(`/api/invoices/${id}`, {}, token),

  createInvoice: (token: string, payload: InvoicePayload) =>
    request<Invoice>("/api/invoices", { method: "POST", body: JSON.stringify(payload) }, token),

  updateInvoice: (token: string, id: string, payload: InvoicePayload) =>
    request<Invoice>(`/api/invoices/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  updateInvoiceStatus: (token: string, id: string, status: InvoiceStatus) =>
    request<Invoice>(`/api/invoices/${id}/status`, { method: "PATCH", body: JSON.stringify({ status }) }, token),

  deleteInvoice: (token: string, id: string) =>
    request<void>(`/api/invoices/${id}`, { method: "DELETE" }, token),

  sendInvoice: (token: string, id: string) =>
    request<Invoice>(`/api/invoices/${id}/send`, { method: "POST" }, token),

  duplicateInvoice: (token: string, id: string) =>
    request<Invoice>(`/api/invoices/${id}/duplicate`, { method: "POST" }, token),

  downloadInvoicePdf: async (token: string, invoiceId: string, invoiceNumber: number) => {
    await downloadFile(`/api/invoices/${invoiceId}/pdf`, token, `invoice-${invoiceNumber}.pdf`, true);
  },

  // For an in-app preview (an <iframe>, not a new tab/download) — caller owns
  // the returned object URL and must URL.revokeObjectURL it when done.
  getInvoicePdfBlobUrl: async (_token: string, invoiceId: string): Promise<string> => {
    const res = await fetch(`/api/invoices/${invoiceId}/pdf`, { credentials: "same-origin" });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Couldn't load the preview");
    }
    const blob = await res.blob();
    return URL.createObjectURL(blob);
  },

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

  markPurchaseOrderPaid: (token: string, id: string) =>
    request<PurchaseOrder>(`/api/purchase-orders/${id}/mark-paid`, { method: "POST" }, token),

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

  startBillingCheckout: (token: string, planId: string, months: number, saveCard: boolean) =>
    request<CheckoutResponse>("/api/billing/checkout", { method: "POST", body: JSON.stringify({ planId, months, saveCard }) }, token),

  verifyBillingPayment: (token: string, reference: string) =>
    request<VerifyPaymentResponse>("/api/billing/verify", { method: "POST", body: JSON.stringify({ reference }) }, token),

  setBillingAutoRenew: (token: string, enabled: boolean) =>
    request<void>("/api/billing/auto-renew", { method: "PATCH", body: JSON.stringify({ enabled }) }, token),

  removeSavedCard: (token: string) =>
    request<void>("/api/billing/saved-card", { method: "DELETE" }, token),

  // --- Business Integrations (Owner only — client's own Paystack/WooCommerce keys) ---

  getBusinessIntegrations: (token: string) => request<BusinessIntegrations>("/api/integrations", {}, token),

  // Unlike getBusinessIntegrations above (OWNER-only, full config), this is safe
  // for any role — receipt pages need it to decide whether to show a "Pay with
  // Paystack" button, and those are viewed by Manager/Sales Person/Accountant too.
  getPaymentGatewayStatus: (token: string) =>
    request<{ paystackConfigured: boolean }>("/api/integrations/payment-status", {}, token),

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

  rescheduleBookingById: (token: string, id: string, scheduledAt: string) =>
    request<void>(`/api/bookings/${id}/reschedule`, { method: "PATCH", body: JSON.stringify({ scheduledAt }) }, token),

  markBookingArrived: (token: string, id: string) =>
    request<void>(`/api/bookings/${id}/mark-arrived`, { method: "POST" }, token),

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

  getPlatformBusinessPaymentTransactions: (
    token: string,
    id: string,
    filters?: { createdBy?: string; from?: string; to?: string }
  ) => {
    const params = new URLSearchParams();
    if (filters?.createdBy) params.set("createdBy", filters.createdBy);
    if (filters?.from) params.set("from", filters.from);
    if (filters?.to) params.set("to", filters.to);
    const qs = params.toString();
    return request<PaymentTransaction[]>(`/api/platform/businesses/${id}/payment-transactions${qs ? `?${qs}` : ""}`, {}, token);
  },

  getPlatformBusinessSubscriptionPayments: (token: string, id: string) =>
    request<SubscriptionPaymentSummary[]>(`/api/platform/businesses/${id}/subscription-payments`, {}, token),

  verifyPlatformBusinessPaymentTransaction: (token: string, businessId: string, transactionId: string) =>
    request<void>(`/api/platform/businesses/${businessId}/payment-transactions/${transactionId}/verify`, { method: "POST" }, token),

  getPlatformBusinessServiceOrders: (token: string, id: string) =>
    request<PlatformServiceOrderSummary[]>(`/api/platform/businesses/${id}/service-orders`, {}, token),

  deletePlatformServiceOrder: (token: string, businessId: string, orderId: string) =>
    request<void>(`/api/platform/businesses/${businessId}/service-orders/${orderId}`, { method: "DELETE" }, token),

  getPlatformBusinessSales: (token: string, id: string) =>
    request<PlatformSaleSummary[]>(`/api/platform/businesses/${id}/sales`, {}, token),

  deletePlatformSale: (token: string, businessId: string, saleId: string) =>
    request<void>(`/api/platform/businesses/${businessId}/sales/${saleId}`, { method: "DELETE" }, token),

  getPlatformBusinessCustomers: (token: string, id: string) =>
    request<PlatformCustomerSummary[]>(`/api/platform/businesses/${id}/customers`, {}, token),

  deletePlatformCustomer: (token: string, businessId: string, customerId: string) =>
    request<void>(`/api/platform/businesses/${businessId}/customers/${customerId}`, { method: "DELETE" }, token),

  getPlatformBusinessExpenses: (token: string, id: string) =>
    request<Expense[]>(`/api/platform/businesses/${id}/expenses`, {}, token),

  getPlatformBusinessCustomWigRequests: (token: string, id: string) =>
    request<CustomWigRequest[]>(`/api/platform/businesses/${id}/custom-wig-requests`, {}, token),

  // Full-record detail views — "for support sake": tapping a Sale/Service
  // Order/Custom Wig Request row shows everything the business's own Owner
  // would see, reusing the same response shapes as the owner-side endpoints.
  getPlatformSaleDetail: (token: string, businessId: string, saleId: string) =>
    request<Sale>(`/api/platform/businesses/${businessId}/sales/${saleId}`, {}, token),

  getPlatformServiceOrderDetail: (token: string, businessId: string, orderId: string) =>
    request<ServiceOrder>(`/api/platform/businesses/${businessId}/service-orders/${orderId}`, {}, token),

  getPlatformCustomWigRequestDetail: (token: string, businessId: string, requestId: string) =>
    request<CustomWigRequestDetail>(`/api/platform/businesses/${businessId}/custom-wig-requests/${requestId}`, {}, token),

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

  updatePlatformBusinessModules: (token: string, id: string, enabledModules: string[]) =>
    request<PlatformBusinessDetail>(
      `/api/platform/businesses/${id}/modules`,
      { method: "PATCH", body: JSON.stringify({ enabledModules }) },
      token
    ),

  deletePlatformBusiness: (token: string, id: string) =>
    request<void>(`/api/platform/businesses/${id}`, { method: "DELETE" }, token),

  // WhatsApp channel binding — Super Admin, developer-configured (Phase 3B).
  // A 404 from getWhatsAppBinding means "not configured yet," not an error.
  getWhatsAppBinding: (token: string, businessId: string) =>
    request<WhatsAppBinding>(`/api/platform/businesses/${businessId}/whatsapp-binding`, {}, token),

  createWhatsAppBinding: (token: string, businessId: string, payload: WhatsAppBindingCreatePayload) =>
    request<WhatsAppBinding>(
      `/api/platform/businesses/${businessId}/whatsapp-binding`,
      { method: "POST", body: JSON.stringify(payload) },
      token
    ),

  updateWhatsAppBinding: (token: string, businessId: string, payload: WhatsAppBindingUpdatePayload) =>
    request<WhatsAppBinding>(
      `/api/platform/businesses/${businessId}/whatsapp-binding`,
      { method: "PUT", body: JSON.stringify(payload) },
      token
    ),

  setWhatsAppBindingActive: (token: string, businessId: string, active: boolean) =>
    request<WhatsAppBinding>(
      `/api/platform/businesses/${businessId}/whatsapp-binding/active`,
      { method: "PATCH", body: JSON.stringify({ active }) },
      token
    ),

  testWhatsAppBindingConnection: (token: string, businessId: string) =>
    request<WhatsAppConnectionTestResult>(
      `/api/platform/businesses/${businessId}/whatsapp-binding/test-connection`,
      { method: "POST" },
      token
    ),

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

  // Staff logging a request that arrived through an informal channel
  // (Instagram DM, WhatsApp, a phone call) — same multipart shape as the
  // public submitCustomWigRequest below, just authenticated.
  createStaffCustomWigRequest: async (
    token: string,
    payload: CreateStaffCustomWigRequestPayload,
    photo: File | null
  ): Promise<CustomWigRequest> => {
    const formData = new FormData();
    formData.append("payload", JSON.stringify(payload));
    if (photo) formData.append("photo", photo);
    const res = await fetch("/api/custom-wig-requests", {
      method: "POST",
      credentials: "same-origin",
      body: formData,
    });
    if (!res.ok) {
      const body = await res.json().catch(() => ({ error: res.statusText }));
      throw new ApiError(res.status, body.error || "Couldn't log that request.");
    }
    return res.json();
  },

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

  updateCustomWigRequestStatus: (token: string, id: string, status: CustomWigRequestStatus) =>
    request<CustomWigRequest>(
      `/api/custom-wig-requests/${id}/status`,
      { method: "PATCH", body: JSON.stringify({ status }) },
      token
    ),

  chargeCustomWigRequestMobileMoney: (token: string, id: string, phone: string, provider: MobileMoneyProvider) =>
    request<MobileMoneyChargeResponse>(
      `/api/custom-wig-requests/${id}/charge-mobile-money`,
      { method: "POST", body: JSON.stringify({ phone, provider }) },
      token
    ),

  verifyCustomWigRequestPayment: (token: string, reference: string) =>
    request<CustomWigRequest>("/api/custom-wig-requests/verify", { method: "POST", body: JSON.stringify({ reference }) }, token),

  submitCustomWigRequestMobileMoneyOtp: (token: string, reference: string, otp: string) =>
    request<CustomWigRequest>(
      "/api/custom-wig-requests/submit-mobile-money-otp",
      { method: "POST", body: JSON.stringify({ reference, otp }) },
      token
    ),

  markCustomWigRequestPaid: (token: string, id: string) =>
    request<CustomWigRequest>(`/api/custom-wig-requests/${id}/mark-paid`, { method: "POST" }, token),

  recordCustomWigRequestPayment: (token: string, id: string, payload: RecordPaymentPayload) =>
    request<CustomWigRequest>(`/api/custom-wig-requests/${id}/record-payment`, { method: "POST", body: JSON.stringify(payload) }, token),

  refundCustomWigRequest: (token: string, id: string, note?: string) =>
    request<CustomWigRequest | PendingApprovalOutcome>(`/api/custom-wig-requests/${id}/refund`, { method: "POST", body: JSON.stringify({ note }) }, token),

  updateCustomWigRequestPrice: (token: string, id: string, finalPrice: number, note?: string) =>
    request<CustomWigRequest | PendingApprovalOutcome>(
      `/api/custom-wig-requests/${id}/price`,
      { method: "PATCH", body: JSON.stringify({ finalPrice, note }) },
      token
    ),

  getOnboardingStatus: (token: string) =>
    request<OnboardingStatus>("/api/users/me/onboarding-status", {}, token),

  completeOnboarding: (token: string) =>
    request<OnboardingStatus>("/api/users/me/complete-onboarding", { method: "POST" }, token),

  listNotifications: (token: string) => request<Notification[]>("/api/notifications", {}, token),

  getUnreadNotificationCount: (token: string) =>
    request<{ count: number }>("/api/notifications/unread-count", {}, token),

  markNotificationRead: (token: string, id: string) =>
    request<void>(`/api/notifications/${id}/read`, { method: "POST" }, token),

  markAllNotificationsRead: (token: string) =>
    request<void>("/api/notifications/read-all", { method: "POST" }, token),

  listHelpRequests: (token: string) => request<HelpRequest[]>("/api/help-requests", {}, token),

  createHelpRequest: (token: string, payload: HelpRequestPayload) =>
    request<HelpRequest>("/api/help-requests", { method: "POST", body: JSON.stringify(payload) }, token),

  listPlatformHelpRequests: (token: string) =>
    request<PlatformHelpRequest[]>("/api/platform/help-requests", {}, token),

  respondPlatformHelpRequest: (token: string, id: string, responseMessage: string) =>
    request<PlatformHelpRequest>(
      `/api/platform/help-requests/${id}/respond`,
      { method: "PATCH", body: JSON.stringify({ response: responseMessage }) },
      token
    ),

  listPendingApprovals: (token: string) => request<PendingApproval[]>("/api/pending-approvals", {}, token),

  approvePendingApproval: (token: string, id: string, note?: string) =>
    request<PendingApproval>(`/api/pending-approvals/${id}/approve`, { method: "POST", body: JSON.stringify({ note }) }, token),

  rejectPendingApproval: (token: string, id: string, note?: string) =>
    request<PendingApproval>(`/api/pending-approvals/${id}/reject`, { method: "POST", body: JSON.stringify({ note }) }, token),

  subscribeToPush: (token: string, payload: { endpoint: string; p256dh: string; auth: string }) =>
    request<void>("/api/push-subscriptions", { method: "POST", body: JSON.stringify(payload) }, token),

  unsubscribeFromPush: (token: string, endpoint: string) =>
    request<void>(`/api/push-subscriptions?endpoint=${encodeURIComponent(endpoint)}`, { method: "DELETE" }, token),

  getAiOverview: (token: string) => request<AiOverview>("/api/ai/overview", {}, token),

  getAiSettings: (token: string) => request<AiSettings>("/api/ai/settings", {}, token),

  updateAiSettings: (token: string, payload: AiSettingsPayload) =>
    request<AiSettings>("/api/ai/settings", { method: "PUT", body: JSON.stringify(payload) }, token),

  listAiKnowledgeEntries: (token: string) => request<AiKnowledgeEntry[]>("/api/ai/knowledge", {}, token),

  createAiKnowledgeEntry: (token: string, payload: AiKnowledgeEntryPayload) =>
    request<AiKnowledgeEntry>("/api/ai/knowledge", { method: "POST", body: JSON.stringify(payload) }, token),

  updateAiKnowledgeEntry: (token: string, id: string, payload: AiKnowledgeEntryPayload) =>
    request<AiKnowledgeEntry>(`/api/ai/knowledge/${id}`, { method: "PUT", body: JSON.stringify(payload) }, token),

  deactivateAiKnowledgeEntry: (token: string, id: string) =>
    request<AiKnowledgeEntry>(`/api/ai/knowledge/${id}/deactivate`, { method: "PATCH" }, token),

  listAiConversations: (token: string) => request<AiConversationSummary[]>("/api/ai/conversations", {}, token),

  getAiConversation: (token: string, id: string) => request<AiConversationDetail>(`/api/ai/conversations/${id}`, {}, token),

  sendAiChatMessage: (token: string, conversationId: string | null, message: string) =>
    request<AiChatResponse>(
      "/api/ai/chat",
      { method: "POST", body: JSON.stringify({ conversationId, message }) },
      token
    ),

  listAiChannels: (token: string) => request<AiChannelStatus[]>("/api/ai/channels", {}, token),
};

export { ApiError };
