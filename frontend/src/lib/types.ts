export type Role = "RESIDENT" | "HELPDESK";

export type Me = {
  id: number;
  name: string;
  role: Role;
  isDutyManager: boolean;
  flats: { id: number; code: string; relation: "OWNER" | "TENANT" | "FAMILY" }[];
};

export type DemoUser = { name: string; phone: string; role: Role; flatCode: string | null; note: string };

export type FlatSummary = {
  flatId: number;
  flatCode: string;
  asOf: string;
  outstanding: string;
  overdue: string;
  advance: string;
  accruedLateFee: string;
  oldestUnpaidDueDate: string | null;
  daysOverdue: number;
};

export type BillStatus = "DUE" | "OVERDUE" | "PAID";

export type Bill = {
  id: number;
  month: string;
  areaSqft: number;
  rate: string;
  amount: string;
  amountPaid: string;
  balance: string;
  dueDate: string;
  status: BillStatus;
};

export type LedgerLine = {
  id: number;
  date: string;
  type: "BILL" | "LATE_FEE" | "PAYMENT" | "REVERSAL";
  description: string;
  debit: string;
  credit: string;
  runningBalance: string;
  receiptNo: string | null;
};

export type PaymentIntent = {
  id: number;
  flatId: number;
  amount: string;
  status: "CREATED" | "SUCCEEDED" | "FAILED";
  gatewayRef: string;
  receiptNo: string | null;
};

export type Receipt = {
  receiptNo: string;
  flatId: number;
  flatCode: string;
  amount: string;
  paidOn: string;
  method: string;
  source: string;
  reference: string | null;
  appliedTo: { description: string; amount: string; appliedOn: string }[];
  heldAsAdvance: string;
  recordedAt: string;
};

export const CATEGORIES = [
  "PLUMBING",
  "ELECTRICAL",
  "LIFT",
  "SECURITY",
  "HOUSEKEEPING",
  "CIVIL",
  "PEST_CONTROL",
  "PARKING",
  "NOISE",
  "OTHER",
] as const;
export type Category = (typeof CATEGORIES)[number];

export const URGENCIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW"] as const;
export type Urgency = (typeof URGENCIES)[number];

export type ComplaintStatus = "OPEN" | "ASSIGNED" | "IN_PROGRESS" | "RESOLVED" | "CLOSED";

export type ComplaintSummary = {
  id: number;
  refNo: string;
  flatId: number;
  flatCode: string;
  summary: string;
  category: Category;
  urgency: Urgency;
  status: ComplaintStatus;
  assigneeName: string | null;
  createdAt: string;
  nextDueAt: string | null;
  overdue: boolean;
  reopenCount: number;
  needsReview: boolean;
  safetyEscalated: boolean;
  escalationOpen: boolean;
  linkedToRefNo: string | null;
  linkedCount: number;
  hasPhoto: boolean;
};

export type TimelineEntry = {
  type: string;
  actorName: string | null;
  actorRole: string | null;
  from: string | null;
  to: string | null;
  note: string | null;
  at: string;
};

export type ComplaintDetail = {
  id: number;
  refNo: string;
  flatId: number;
  flatCode: string;
  raisedBy: string;
  description: string;
  hasPhoto: boolean;
  category: Category;
  urgency: Urgency;
  status: ComplaintStatus;
  assignee: { id: number; name: string } | null;
  createdAt: string;
  firstResponseDueAt: string;
  firstResponseAt: string | null;
  resolveDueAt: string;
  resolvedAt: string | null;
  closedAt: string | null;
  nextDueAt: string | null;
  overdue: boolean;
  reopenCount: number;
  rating: number | null;
  ratingComment: string | null;
  classifiedBy: "AI" | "RULES_FALLBACK";
  classificationStatus: "PENDING" | "DONE" | "FAILED";
  aiCategory: Category | null;
  aiUrgency: Urgency | null;
  aiConfidence: string | null;
  aiSummary: string | null;
  aiReason: string | null;
  needsReview: boolean;
  safetyEscalated: boolean;
  safetyTerms: string[];
  escalation: {
    id: number;
    dutyManager: string;
    reason: string;
    raisedAt: string;
    acknowledgedBy: string | null;
    acknowledgedAt: string | null;
  } | null;
  link: {
    root: boolean;
    rootId: number;
    rootRefNo: string;
    flatsReporting: number;
    others: { id: number; refNo: string; flatCode: string; status: ComplaintStatus }[];
  } | null;
  timeline: TimelineEntry[];
  actions: {
    canClose: boolean;
    canReopen: boolean;
    canComment: boolean;
    canReassign: boolean;
    canRecategorise: boolean;
    canAcknowledgeEscalation: boolean;
    canUnlink: boolean;
    canApplyToLinked: boolean;
    statusChanges: ComplaintStatus[];
  };
  linkedUpdate: {
    applied: string[];
    skipped: { id: number; refNo: string; status: ComplaintStatus; reason: string }[];
  } | null;
};

export type StaffMember = { id: number; name: string; skills: Category[]; dutyManager: boolean };

export type Decision =
  | "AUTO_POSTED"
  | "RECONCILED"
  | "SUGGESTED"
  | "CONFLICT"
  | "UNMATCHED"
  | "DUPLICATE"
  | "POSSIBLE_DUPLICATE"
  | "NON_CREDIT"
  | "PARSE_ERROR";

export type FlatRef = { id: number; code: string };

export type BankRow = {
  id: number;
  importId: number;
  rowNo: number;
  txnDate: string | null;
  description: string | null;
  reference: string | null;
  amount: string | null;
  debit: string | null;
  utr: string | null;
  upiId: string | null;
  gatewayRef: string | null;
  decision: Decision;
  confidence: "HIGH" | "MEDIUM" | "LOW" | null;
  suggestedFlat: FlatRef | null;
  candidates: FlatRef[];
  reasons: string[];
  duplicateOf: { rowId: number; importId: number; rowNo: number } | null;
  receiptNo: string | null;
  resolution: "CONFIRMED" | "ASSIGNED" | "IGNORED" | null;
  resolvedBy: string | null;
  resolvedAt: string | null;
  resolutionNote: string | null;
  parseError: string | null;
  openInQueue: boolean;
};

export type BankImportSummary = {
  id: number;
  fileName: string;
  uploadedAt: string;
  uploadedBy: string;
  totalRows: number;
  autoPosted: number;
  reconciled: number;
  suggested: number;
  conflicts: number;
  unmatched: number;
  duplicates: number;
  possibleDuplicates: number;
  nonCredit: number;
  errors: number;
  sameFileImportedBefore: string | null;
};

export type BankImportDetails = { summary: BankImportSummary; rows: BankRow[] };

export type QueueActionResponse = { row: BankRow; warnings: string[] };

export type FlatSearchResult = { id: number; code: string; residents: string[] };

export type CommitteeOverview = {
  month: string;
  asOf: string;
  generatedAt: string;
  collections: {
    billed: string;
    lateFeesCharged: string;
    lateFeeReversals: string;
    collected: string;
    totalDue: string;
    collectionRate: string | null;
    billsDue: number;
    billsPaidOnTime: number;
    onTimeRate: string | null;
    onTimeTarget: string;
  };
  reconciliation: {
    openingOutstanding: string;
    billed: string;
    lateFeesCharged: string;
    lateFeeReversals: string;
    collections: string;
    expectedClosingOutstanding: string;
    closingOutstanding: string;
    balanced: boolean;
  };
  ageing: {
    asOf: string;
    buckets: { bucket: string; label: string; flats: number; amount: string }[];
    total: string;
    matchesClosingOutstanding: boolean;
  };
  matching: {
    lastImport: {
      id: number;
      fileName: string;
      uploadedAt: string;
      totalRows: number;
      autoPosted: number;
      reconciled: number;
      suggested: number;
      conflicts: number;
      unmatched: number;
      duplicates: number;
      possibleDuplicates: number;
      nonCredit: number;
      errors: number;
    } | null;
    importsThisMonth: number;
    newPaymentRows: number;
    autoPosted: number;
    autoPostShare: string | null;
    queueSize: number;
    oldestQueuedAt: string | null;
    oldestQueuedDays: number | null;
  };
  complaints: {
    opened: number;
    resolved: number;
    medianFirstResponseMinutes: number | null;
    medianResolutionMinutes: number | null;
    firstResponseMeasured: number;
    firstResponseBreached: number;
    firstResponseBreachShare: string | null;
    reopened: number;
    reopenRate: string | null;
    reopenTarget: string;
    ratings: number;
    averageRating: string | null;
    openByCategory: { category: Category; open: number }[];
    activeSafetyEscalations: number;
    unacknowledgedEscalations: number;
  };
};

export type AgeingFlats = {
  month: string;
  asOf: string;
  bucket: string;
  label: string;
  flats: number;
  amount: string;
  items: { flatId: number; flatCode: string; amount: string; oldestUnpaidDue: string | null; daysOverdue: number }[];
};
