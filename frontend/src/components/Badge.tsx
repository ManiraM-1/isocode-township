import { ReactNode } from "react";
import { BillStatus, ComplaintStatus, Decision, Urgency } from "../lib/types";

export type Tone = "neutral" | "accent" | "danger" | "caution" | "muted";

const TONES: Record<Tone, string> = {
  neutral: "bg-line/60 text-ink",
  accent: "bg-accent-soft text-accent",
  danger: "bg-danger-soft text-danger",
  caution: "bg-caution-soft text-caution",
  muted: "bg-transparent text-ink-faint border border-line",
};

export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span
      className={`inline-flex items-center gap-1 whitespace-nowrap rounded-full px-2.5 py-0.5 text-xs font-medium ${TONES[tone]}`}
    >
      {children}
    </span>
  );
}

const STATUS_LABEL: Record<ComplaintStatus, string> = {
  OPEN: "Open",
  ASSIGNED: "Assigned",
  IN_PROGRESS: "In progress",
  RESOLVED: "Resolved",
  CLOSED: "Closed",
};

const STATUS_TONE: Record<ComplaintStatus, Tone> = {
  OPEN: "neutral",
  ASSIGNED: "neutral",
  IN_PROGRESS: "accent",
  RESOLVED: "accent",
  CLOSED: "muted",
};

export function statusLabel(status: ComplaintStatus) {
  return STATUS_LABEL[status];
}

export function StatusBadge({ status }: { status: ComplaintStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>;
}

const URGENCY_TONE: Record<Urgency, Tone> = { CRITICAL: "danger", HIGH: "caution", MEDIUM: "neutral", LOW: "muted" };

export function UrgencyBadge({ urgency }: { urgency: Urgency }) {
  return <Badge tone={URGENCY_TONE[urgency]}>{urgency.charAt(0) + urgency.slice(1).toLowerCase()}</Badge>;
}

const BILL_LABEL: Record<BillStatus, [string, Tone]> = {
  PAID: ["Paid", "accent"],
  DUE: ["Due", "neutral"],
  OVERDUE: ["Overdue", "danger"],
};

export function BillStatusBadge({ status }: { status: BillStatus }) {
  const [label, tone] = BILL_LABEL[status];
  return <Badge tone={tone}>{label}</Badge>;
}

const DECISION_LABEL: Record<Decision, [string, Tone]> = {
  AUTO_POSTED: ["Posted", "accent"],
  RECONCILED: ["Reconciled", "accent"],
  SUGGESTED: ["Suggested", "caution"],
  CONFLICT: ["Conflict", "danger"],
  UNMATCHED: ["Unmatched", "caution"],
  DUPLICATE: ["Duplicate", "muted"],
  POSSIBLE_DUPLICATE: ["Possible duplicate", "caution"],
  NON_CREDIT: ["Not a credit", "muted"],
  PARSE_ERROR: ["Unreadable", "danger"],
};

export function DecisionBadge({ decision }: { decision: Decision }) {
  const [label, tone] = DECISION_LABEL[decision];
  return <Badge tone={tone}>{label}</Badge>;
}

export function decisionLabel(decision: Decision) {
  return DECISION_LABEL[decision][0];
}
