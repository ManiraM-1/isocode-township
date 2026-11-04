import { FormEvent, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { api, ApiError } from "../../lib/api";
import { CATEGORY_LABEL, URGENCY_LABEL } from "../../lib/complaints";
import { formatDateTime } from "../../lib/format";
import { CATEGORIES, Category, ComplaintDetail, ComplaintStatus, URGENCIES, Urgency } from "../../lib/types";
import { Badge, StatusBadge, statusLabel, UrgencyBadge } from "../../components/Badge";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { Page } from "../../components/Page";
import { ErrorState, InlineError, Skeleton } from "../../components/States";
import { Timeline } from "../../components/Timeline";
import { useToast } from "../../components/Toast";
import { AcknowledgeButton } from "./ComplaintBoard";
import { deskKeys, useDeskComplaint, useStaff } from "./queries";

export function DeskComplaintDetail() {
  const id = Number(useParams().complaintId);
  const complaint = useDeskComplaint(id);
  return (
    <Page
      width="wide"
      title={complaint.data?.refNo ?? "Complaint"}
      subtitle={
        complaint.data &&
        `${complaint.data.flatCode} · ${complaint.data.raisedBy} · raised ${formatDateTime(complaint.data.createdAt)}`
      }
      back={{ to: "/desk/complaints", label: "Board" }}
    >
      {complaint.isPending ? (
        <div className="grid gap-4 lg:grid-cols-[1fr_360px]">
          <Skeleton className="h-72 w-full" />
          <Skeleton className="h-72 w-full" />
        </div>
      ) : complaint.isError ? (
        <ErrorState error={complaint.error} onRetry={() => complaint.refetch()} />
      ) : (
        <DetailBody complaint={complaint.data} />
      )}
    </Page>
  );
}

function useDeskAction<T>(complaint: ComplaintDetail, send: (body: T) => Promise<ComplaintDetail>, message?: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: send,
    onSuccess: (updated) => {
      queryClient.setQueryData(deskKeys.complaint(complaint.id), updated);
      queryClient.invalidateQueries({ queryKey: deskKeys.board });
      if (message) toast(message);
    },
  });
}

function DetailBody({ complaint }: { complaint: ComplaintDetail }) {
  return (
    <div className="grid gap-4 lg:grid-cols-[1fr_360px] lg:items-start">
      <div className="space-y-4 lg:col-start-1">
        {complaint.escalation && <EscalationCard complaint={complaint} />}
        <Card className="p-5">
          <div className="flex flex-wrap items-center gap-2">
            <StatusBadge status={complaint.status} />
            <Badge tone="neutral">{CATEGORY_LABEL[complaint.category]}</Badge>
            <UrgencyBadge urgency={complaint.urgency} />
            {complaint.needsReview && <Badge tone="caution">Needs review</Badge>}
            {complaint.overdue && <Badge tone="danger">Overdue</Badge>}
          </div>
          <p className="mt-3 whitespace-pre-line text-[15px] text-ink">{complaint.description}</p>
          {complaint.hasPhoto && (
            <a href={`/api/complaints/${complaint.id}/photo`} target="_blank" rel="noreferrer">
              <img
                src={`/api/complaints/${complaint.id}/photo`}
                alt="Photo from the resident"
                className="mt-4 max-h-80 rounded-xl border border-line object-contain"
              />
            </a>
          )}
          <dl className="mt-5 grid grid-cols-2 gap-2 border-t border-line pt-4 text-sm">
            <Fact label="First response due" value={formatDateTime(complaint.firstResponseDueAt)} />
            <Fact label="First response" value={complaint.firstResponseAt ? formatDateTime(complaint.firstResponseAt) : "Not yet"} />
            <Fact label="Resolve by" value={formatDateTime(complaint.resolveDueAt)} />
            <Fact label="Reopened" value={complaint.reopenCount === 0 ? "Never" : `${complaint.reopenCount} times`} />
          </dl>
        </Card>
        <AiCard complaint={complaint} />
        <LinkedCard complaint={complaint} />
      </div>
      <div className="space-y-4 lg:col-start-2 lg:row-span-2 lg:row-start-1">
        {complaint.actions.canRecategorise && <CategoryCard complaint={complaint} />}
        <StatusCard complaint={complaint} />
        {complaint.actions.canReassign && <AssigneeCard complaint={complaint} />}
        {complaint.actions.canComment && <CommentCard complaint={complaint} />}
      </div>
      <Card className="p-5 lg:col-start-1">
        <h2 className="mb-4 text-[15px] font-semibold text-ink">Timeline</h2>
        <Timeline entries={complaint.timeline} />
      </Card>
    </div>
  );
}

function Fact({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-ink-faint">{label}</dt>
      <dd className="tabular-nums text-ink">{value}</dd>
    </div>
  );
}

function EscalationCard({ complaint }: { complaint: ComplaintDetail }) {
  const escalation = complaint.escalation!;
  const open = !escalation.acknowledgedAt;
  return (
    <Card className={`p-5 ${open ? "border-danger/50 bg-danger-soft/40" : ""}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <p className={`text-sm font-semibold ${open ? "text-danger" : "text-ink"}`}>
            {open ? "Safety escalation, not yet acknowledged" : "Safety escalation acknowledged"}
          </p>
          <p className="mt-1 text-sm text-ink-soft">{escalation.reason}</p>
          <p className="mt-1 text-xs text-ink-faint">
            Duty manager {escalation.dutyManager} · raised {formatDateTime(escalation.raisedAt)}
            {escalation.acknowledgedAt &&
              ` · acknowledged by ${escalation.acknowledgedBy} ${formatDateTime(escalation.acknowledgedAt)}`}
          </p>
          {complaint.safetyTerms.length > 0 && (
            <p className="mt-2 flex flex-wrap gap-1">
              {complaint.safetyTerms.map((term) => (
                <Badge key={term} tone="danger">
                  {term}
                </Badge>
              ))}
            </p>
          )}
        </div>
        {complaint.actions.canAcknowledgeEscalation && (
          <AcknowledgeButton escalationId={escalation.id} refNo={complaint.refNo} />
        )}
      </div>
    </Card>
  );
}

function AiCard({ complaint }: { complaint: ComplaintDetail }) {
  if (complaint.classificationStatus === "PENDING") {
    return (
      <Card className="p-5">
        <p className="text-sm font-medium text-ink">AI is sorting this complaint…</p>
        <p className="mt-1 text-sm text-ink-soft">The keyword rules filled in the category for now.</p>
      </Card>
    );
  }
  const fallback = complaint.classifiedBy === "RULES_FALLBACK";
  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">{fallback ? "Keyword rules (AI unavailable)" : "AI suggestion"}</h2>
      {complaint.aiCategory ? (
        <p className="mt-2 text-sm text-ink">
          {CATEGORY_LABEL[complaint.aiCategory]}
          {complaint.aiUrgency && `, ${URGENCY_LABEL[complaint.aiUrgency].toLowerCase()}`}
          {complaint.aiConfidence && (
            <span className="text-ink-soft"> · {Math.round(Number(complaint.aiConfidence) * 100)}% confident</span>
          )}
        </p>
      ) : (
        <p className="mt-2 text-sm text-ink-soft">No AI answer was recorded.</p>
      )}
      {complaint.aiSummary && <p className="mt-1 text-sm text-ink-soft">“{complaint.aiSummary}”</p>}
      {complaint.aiReason && <p className="mt-1 text-xs text-ink-faint">{complaint.aiReason}</p>}
    </Card>
  );
}

function CategoryCard({ complaint }: { complaint: ComplaintDetail }) {
  const change = useDeskAction(
    complaint,
    (body: { category: Category; urgency?: Urgency }) =>
      api.patch<ComplaintDetail>(`/api/desk/complaints/${complaint.id}/category`, body),
    "Category saved",
  );
  const pendingCategory = change.isPending ? change.variables?.category : undefined;

  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">Category</h2>
      <p className="mt-0.5 text-xs text-ink-faint">One tap saves. Tapping the current one confirms it.</p>
      <div className="mt-3 flex flex-wrap gap-2" role="group" aria-label="Category">
        {CATEGORIES.map((category) => {
          const current = category === complaint.category;
          return (
            <button
              key={category}
              type="button"
              aria-pressed={current}
              disabled={change.isPending}
              onClick={() => change.mutate({ category })}
              className={`min-h-touch rounded-full border px-3.5 text-sm font-medium transition-colors disabled:opacity-60 ${
                current
                  ? "border-accent bg-accent text-white"
                  : pendingCategory === category
                    ? "border-accent text-accent"
                    : "border-line-strong bg-surface text-ink hover:border-accent"
              }`}
            >
              {CATEGORY_LABEL[category]}
            </button>
          );
        })}
      </div>
      <label className="mt-4 block text-sm font-medium text-ink" htmlFor="urgency">
        Urgency
      </label>
      <select
        id="urgency"
        className="mt-1.5 min-h-touch w-full rounded-xl border border-line-strong bg-surface px-3 text-sm focus:border-accent focus:outline-none"
        value={complaint.urgency}
        disabled={change.isPending}
        onChange={(event) => change.mutate({ category: complaint.category, urgency: event.target.value as Urgency })}
      >
        {URGENCIES.map((urgency) => (
          <option key={urgency} value={urgency}>
            {URGENCY_LABEL[urgency]}
          </option>
        ))}
      </select>
      <div className="mt-3">
        <InlineError error={change.error} />
      </div>
    </Card>
  );
}

const STATUS_ACTION: Record<ComplaintStatus, string> = {
  OPEN: "Reopen",
  ASSIGNED: "Mark assigned",
  IN_PROGRESS: "Start work",
  RESOLVED: "Mark resolved",
  CLOSED: "Close",
};

function StatusCard({ complaint }: { complaint: ComplaintDetail }) {
  const [note, setNote] = useState("");
  const [applyToLinked, setApplyToLinked] = useState(false);
  const change = useDeskAction(
    complaint,
    (body: { status: ComplaintStatus; note: string | null; applyToLinked: boolean }) =>
      api.patch<ComplaintDetail>(`/api/desk/complaints/${complaint.id}/status`, body),
  );
  const toast = useToast();
  const moves = complaint.actions.statusChanges;
  const result = change.data?.linkedUpdate;

  function move(status: ComplaintStatus) {
    change.mutate(
      { status, note: note.trim() || null, applyToLinked },
      {
        onSuccess: () => {
          setNote("");
          toast(`Marked ${statusLabel(status).toLowerCase()}`);
        },
      },
    );
  }

  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">Status</h2>
      {moves.length === 0 ? (
        <p className="mt-2 text-sm text-ink-soft">
          {complaint.status === "RESOLVED"
            ? "Waiting for the resident to close or reopen it."
            : complaint.status === "CLOSED"
              ? "Closed by the resident."
              : "No status change is available."}
        </p>
      ) : (
        <>
          <label htmlFor="status-note" className="mt-3 block text-sm font-medium text-ink">
            Note for the resident (optional)
          </label>
          <textarea
            id="status-note"
            rows={2}
            maxLength={500}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            className="mt-1.5 block w-full rounded-xl border border-line-strong bg-surface px-3.5 py-2.5 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
          />
          {complaint.actions.canApplyToLinked && (
            <label className="mt-3 flex min-h-touch items-center gap-3 text-sm text-ink">
              <input
                type="checkbox"
                checked={applyToLinked}
                onChange={(event) => setApplyToLinked(event.target.checked)}
                className="h-5 w-5 rounded border-line-strong accent-[#1F5C45]"
              />
              Apply to linked complaints too
            </label>
          )}
          <div className="mt-3 flex flex-wrap gap-2">
            {moves.map((status) => (
              <Button
                key={status}
                size="sm"
                variant={status === "RESOLVED" ? "primary" : "secondary"}
                busy={change.isPending && change.variables?.status === status}
                disabled={change.isPending}
                onClick={() => move(status)}
              >
                {STATUS_ACTION[status]}
              </Button>
            ))}
          </div>
        </>
      )}
      <div className="mt-3 space-y-2">
        <InlineError error={change.error} />
        {result && (
          <div className="rounded-xl bg-canvas px-3 py-2 text-sm">
            {result.applied.length > 0 && <p className="text-ink">Also updated {result.applied.join(", ")}.</p>}
            {result.skipped.map((skipped) => (
              <p key={skipped.id} className="text-caution">
                {skipped.refNo} left as {statusLabel(skipped.status).toLowerCase()}: {skipped.reason}
              </p>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}

function AssigneeCard({ complaint }: { complaint: ComplaintDetail }) {
  const staff = useStaff();
  const change = useDeskAction(
    complaint,
    (assigneeId: number) => api.patch<ComplaintDetail>(`/api/desk/complaints/${complaint.id}/assignee`, { assigneeId }),
    "Reassigned",
  );
  const suitable = (staff.data ?? []).filter((member) => member.skills.includes(complaint.category));
  const others = (staff.data ?? []).filter((member) => !member.skills.includes(complaint.category));
  return (
    <Card className="p-5">
      <label htmlFor="assignee" className="text-[15px] font-semibold text-ink">
        Assigned to
      </label>
      {staff.isError ? (
        <div className="mt-2">
          <InlineError error={staff.error} />
        </div>
      ) : (
        <select
          id="assignee"
          className="mt-2 min-h-touch w-full rounded-xl border border-line-strong bg-surface px-3 text-sm focus:border-accent focus:outline-none"
          value={complaint.assignee?.id ?? ""}
          disabled={change.isPending || staff.isPending}
          onChange={(event) => change.mutate(Number(event.target.value))}
        >
          {!complaint.assignee && <option value="">Choose someone</option>}
          <optgroup label={`Handles ${CATEGORY_LABEL[complaint.category].toLowerCase()}`}>
            {suitable.map((member) => (
              <option key={member.id} value={member.id}>
                {member.name}
              </option>
            ))}
          </optgroup>
          {others.length > 0 && (
            <optgroup label="Others">
              {others.map((member) => (
                <option key={member.id} value={member.id}>
                  {member.name}
                </option>
              ))}
            </optgroup>
          )}
        </select>
      )}
      <div className="mt-2">
        <InlineError error={change.error} />
      </div>
    </Card>
  );
}

function CommentCard({ complaint }: { complaint: ComplaintDetail }) {
  const [text, setText] = useState("");
  const [touched, setTouched] = useState(false);
  const comment = useDeskAction(
    complaint,
    (body: { text: string }) => api.post<ComplaintDetail>(`/api/desk/complaints/${complaint.id}/comments`, body),
    "Comment added",
  );
  const validation = text.trim() ? null : "Write a comment";
  const serverError = comment.error instanceof ApiError ? comment.error.fieldMessage("text") : undefined;

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (!validation) {
      comment.mutate({ text: text.trim() }, { onSuccess: () => { setText(""); setTouched(false); } });
    }
  }

  return (
    <Card className="p-5">
      <form onSubmit={submit} noValidate>
        <label htmlFor="comment" className="text-[15px] font-semibold text-ink">
          Comment
        </label>
        <p className="text-xs text-ink-faint">The resident sees this in their timeline.</p>
        <textarea
          id="comment"
          rows={3}
          maxLength={1000}
          value={text}
          aria-invalid={touched && !!validation}
          onChange={(event) => setText(event.target.value)}
          className="mt-2 block w-full rounded-xl border border-line-strong bg-surface px-3.5 py-2.5 text-sm focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
        />
        {((touched && validation) || serverError) && (
          <p className="mt-1.5 text-sm text-danger">{(touched && validation) || serverError}</p>
        )}
        {!serverError && (
          <div className="mt-2">
            <InlineError error={comment.error} />
          </div>
        )}
        <Button type="submit" size="sm" className="mt-3" busy={comment.isPending}>
          Add comment
        </Button>
      </form>
    </Card>
  );
}

function LinkedCard({ complaint }: { complaint: ComplaintDetail }) {
  const unlink = useDeskAction(
    complaint,
    () => api.post<ComplaintDetail>(`/api/desk/complaints/${complaint.id}/unlink`),
    "Unlinked. It is now handled on its own.",
  );
  if (!complaint.link) return null;
  return (
    <Card className="p-5">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-[15px] font-semibold text-ink">
            Linked · {complaint.link.flatsReporting} flats reported this
          </h2>
          <p className="mt-0.5 text-sm text-ink-soft">
            {complaint.link.root ? "This is the first report." : `Linked to ${complaint.link.rootRefNo}.`} Each resident
            keeps their own complaint.
          </p>
        </div>
        {complaint.actions.canUnlink && (
          <Button size="sm" variant="secondary" busy={unlink.isPending} onClick={() => unlink.mutate(undefined)}>
            Unlink
          </Button>
        )}
      </div>
      <ul className="mt-3 divide-y divide-line text-sm">
        {complaint.link.others.map((other) => (
          <li key={other.id} className="flex items-center justify-between gap-3 py-2">
            <Link to={`/desk/complaints/${other.id}`} className="font-medium text-accent hover:underline">
              {other.refNo}
            </Link>
            <span className="text-ink-soft">{other.flatCode}</span>
            <StatusBadge status={other.status} />
          </li>
        ))}
      </ul>
      <div className="mt-2">
        <InlineError error={unlink.error} />
      </div>
    </Card>
  );
}
