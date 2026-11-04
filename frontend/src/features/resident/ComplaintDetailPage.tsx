import { FormEvent, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useParams, useSearchParams } from "react-router-dom";
import { api, ApiError } from "../../lib/api";
import { CATEGORY_LABEL } from "../../lib/complaints";
import { formatDateTime } from "../../lib/format";
import { ComplaintDetail } from "../../lib/types";
import { Badge, StatusBadge, UrgencyBadge } from "../../components/Badge";
import { Button } from "../../components/Button";
import { Card } from "../../components/Card";
import { Page } from "../../components/Page";
import { ErrorState, InlineError, Skeleton } from "../../components/States";
import { Timeline } from "../../components/Timeline";
import { useToast } from "../../components/Toast";
import { residentKeys, useComplaint } from "./queries";

export function ComplaintDetailPage() {
  const id = Number(useParams().complaintId);
  const [params] = useSearchParams();
  const complaint = useComplaint(id);

  return (
    <Page
      title={complaint.data ? complaint.data.refNo : "Complaint"}
      subtitle={complaint.data ? `Raised ${formatDateTime(complaint.data.createdAt)}` : undefined}
      back={{ to: "/complaints", label: "Complaints" }}
    >
      {complaint.isPending ? (
        <div className="space-y-4">
          <Skeleton className="h-40 w-full" />
          <Skeleton className="h-56 w-full" />
        </div>
      ) : complaint.isError ? (
        <ErrorState error={complaint.error} onRetry={() => complaint.refetch()} />
      ) : (
        <ComplaintBody complaint={complaint.data} justRaised={params.get("new") === "1"} />
      )}
    </Page>
  );
}

function ComplaintBody({ complaint, justRaised }: { complaint: ComplaintDetail; justRaised: boolean }) {
  const sorting = complaint.classificationStatus === "PENDING";
  return (
    <div className="space-y-4">
      {justRaised && (
        <p className="rounded-xl bg-accent-soft px-4 py-3 text-sm text-accent">
          Sent. The help desk has your complaint{complaint.assignee ? ` and ${complaint.assignee.name} is on it` : ""}.
        </p>
      )}

      {complaint.safetyEscalated && (
        <p role="alert" className="rounded-xl bg-danger-soft px-4 py-3 text-sm text-danger">
          This looks like a safety issue, so the duty manager{complaint.escalation ? `, ${complaint.escalation.dutyManager},` : ""} has
          been alerted. If anyone is in danger, leave the area and call 112.
        </p>
      )}

      <Card className="p-5">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={complaint.status} />
          {sorting ? (
            <Badge tone="muted">Sorting…</Badge>
          ) : (
            <>
              <Badge tone="neutral">{CATEGORY_LABEL[complaint.category]}</Badge>
              <UrgencyBadge urgency={complaint.urgency} />
            </>
          )}
        </div>
        <p className="mt-3 whitespace-pre-line text-[15px] text-ink">{complaint.description}</p>
        {complaint.hasPhoto && (
          <img
            src={`/api/complaints/${complaint.id}/photo`}
            alt="Photo attached to the complaint"
            className="mt-4 max-h-72 w-full rounded-xl border border-line object-cover"
          />
        )}
        <dl className="mt-5 space-y-2 border-t border-line pt-4 text-sm">
          <div className="flex justify-between gap-4">
            <dt className="text-ink-soft">Handled by</dt>
            <dd className="text-right font-medium text-ink">{complaint.assignee?.name ?? "Help desk"}</dd>
          </div>
          <ResponseTimer complaint={complaint} />
        </dl>
      </Card>

      {complaint.link && (
        <Card className="p-5">
          <p className="text-sm font-medium text-ink">
            {complaint.link.flatsReporting} flats reported this
          </p>
          <p className="mt-1 text-sm text-ink-soft">
            Your complaint is linked to {complaint.link.root ? "the others" : complaint.link.rootRefNo} so one team fixes it
            for everyone. It stays open in your name, and you can still rate or reopen it.
          </p>
        </Card>
      )}

      {complaint.actions.canClose && <ResolveActions complaint={complaint} />}

      {complaint.rating && (
        <Card className="p-5">
          <p className="text-sm text-ink-soft">You rated the fix</p>
          <p className="mt-1 text-lg text-ink" aria-label={`${complaint.rating} out of 5`}>
            {"★".repeat(complaint.rating)}
            <span className="text-line-strong">{"★".repeat(5 - complaint.rating)}</span>
          </p>
          {complaint.ratingComment && <p className="mt-1 text-sm text-ink-soft">{complaint.ratingComment}</p>}
        </Card>
      )}

      <Card className="p-5">
        <h2 className="mb-4 text-[15px] font-semibold text-ink">Updates</h2>
        <Timeline entries={complaint.timeline} />
      </Card>
    </div>
  );
}

function ResponseTimer({ complaint }: { complaint: ComplaintDetail }) {
  if (complaint.status === "CLOSED") {
    return <TimerRow label="Closed" value={formatDateTime(complaint.closedAt)} />;
  }
  if (complaint.status === "RESOLVED") {
    return <TimerRow label="Resolved" value={formatDateTime(complaint.resolvedAt)} />;
  }
  if (!complaint.firstResponseAt) {
    return (
      <TimerRow
        label="You can expect a first response by"
        value={formatDateTime(complaint.firstResponseDueAt)}
        late={complaint.overdue}
      />
    );
  }
  return (
    <>
      <TimerRow label="First response" value={formatDateTime(complaint.firstResponseAt)} />
      <TimerRow label="Target fix by" value={formatDateTime(complaint.resolveDueAt)} late={complaint.overdue} />
    </>
  );
}

function TimerRow({ label, value, late = false }: { label: string; value: string; late?: boolean }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-ink-soft">{label}</dt>
      <dd className={`text-right font-medium tabular-nums ${late ? "text-danger" : "text-ink"}`}>
        {value}
        {late && " · running late"}
      </dd>
    </div>
  );
}

function ResolveActions({ complaint }: { complaint: ComplaintDetail }) {
  const [mode, setMode] = useState<"choose" | "close" | "reopen">("choose");
  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">Is it fixed?</h2>
      <p className="mt-1 text-sm text-ink-soft">The help desk marked this resolved. Please confirm.</p>
      {mode === "choose" && (
        <div className="mt-4 grid grid-cols-2 gap-3">
          <Button onClick={() => setMode("close")}>Yes, close it</Button>
          <Button variant="secondary" onClick={() => setMode("reopen")}>
            No, reopen
          </Button>
        </div>
      )}
      {mode === "close" && <CloseForm complaint={complaint} onCancel={() => setMode("choose")} />}
      {mode === "reopen" && <ReopenForm complaint={complaint} onCancel={() => setMode("choose")} />}
    </Card>
  );
}

function useComplaintAction(complaint: ComplaintDetail, path: string, successMessage: string) {
  const queryClient = useQueryClient();
  const toast = useToast();
  return useMutation({
    mutationFn: (body: unknown) => api.post<ComplaintDetail>(`/api/complaints/${complaint.id}/${path}`, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(residentKeys.complaint(complaint.id), updated);
      queryClient.invalidateQueries({ queryKey: residentKeys.complaints(complaint.flatId) });
      toast(successMessage);
    },
  });
}

function CloseForm({ complaint, onCancel }: { complaint: ComplaintDetail; onCancel: () => void }) {
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState("");
  const [touched, setTouched] = useState(false);
  const close = useComplaintAction(complaint, "close", "Thanks. The complaint is closed.");

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (rating > 0) close.mutate({ rating, comment: comment.trim() || null });
  }

  return (
    <form onSubmit={submit} noValidate className="mt-4 space-y-4">
      <fieldset>
        <legend className="mb-2 text-sm font-medium text-ink">How was the fix?</legend>
        <div className="flex gap-1" role="radiogroup">
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={rating === value}
              aria-label={`${value} out of 5`}
              onClick={() => setRating(value)}
              className={`grid h-11 w-11 place-items-center rounded-xl text-2xl ${
                value <= rating ? "text-accent" : "text-line-strong hover:text-ink-faint"
              }`}
            >
              ★
            </button>
          ))}
        </div>
        {touched && rating === 0 && <p className="mt-1.5 text-sm text-danger">Choose a rating from 1 to 5</p>}
      </fieldset>
      <div>
        <label htmlFor="rating-comment" className="mb-1.5 block text-sm font-medium text-ink">
          Comment (optional)
        </label>
        <textarea
          id="rating-comment"
          rows={2}
          maxLength={500}
          value={comment}
          onChange={(event) => setComment(event.target.value)}
          className="block w-full rounded-xl border border-line-strong bg-surface px-3.5 py-2.5 text-[15px] focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
        />
      </div>
      <InlineError error={close.error} />
      <div className="grid grid-cols-2 gap-3">
        <Button type="submit" busy={close.isPending}>
          Close complaint
        </Button>
        <Button variant="ghost" onClick={onCancel} disabled={close.isPending}>
          Back
        </Button>
      </div>
    </form>
  );
}

function ReopenForm({ complaint, onCancel }: { complaint: ComplaintDetail; onCancel: () => void }) {
  const [reason, setReason] = useState("");
  const [touched, setTouched] = useState(false);
  const reopen = useComplaintAction(complaint, "reopen", "Reopened. The same person will look at it again.");
  const trimmed = reason.trim();
  const validation = trimmed.length < 5 ? "Tell us what is still wrong (at least 5 characters)" : null;
  const serverError = reopen.error instanceof ApiError ? reopen.error.fieldMessage("reason") : undefined;

  function submit(event: FormEvent) {
    event.preventDefault();
    setTouched(true);
    if (!validation) reopen.mutate({ reason: trimmed });
  }

  return (
    <form onSubmit={submit} noValidate className="mt-4 space-y-4">
      <div>
        <label htmlFor="reopen-reason" className="mb-1.5 block text-sm font-medium text-ink">
          What is still wrong?
        </label>
        <textarea
          id="reopen-reason"
          rows={3}
          maxLength={500}
          value={reason}
          aria-invalid={touched && !!validation}
          onChange={(event) => setReason(event.target.value)}
          onBlur={() => setTouched(true)}
          className="block w-full rounded-xl border border-line-strong bg-surface px-3.5 py-2.5 text-[15px] focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/30"
        />
        {((touched && validation) || serverError) && (
          <p className="mt-1.5 text-sm text-danger">{(touched && validation) || serverError}</p>
        )}
      </div>
      {!serverError && <InlineError error={reopen.error} />}
      <div className="grid grid-cols-2 gap-3">
        <Button type="submit" busy={reopen.isPending}>
          Reopen complaint
        </Button>
        <Button variant="ghost" onClick={onCancel} disabled={reopen.isPending}>
          Back
        </Button>
      </div>
    </form>
  );
}
