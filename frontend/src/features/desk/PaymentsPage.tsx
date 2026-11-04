import { DragEvent, useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate } from "react-router-dom";
import { api, downloadFile } from "../../lib/api";
import { formatDateTime } from "../../lib/format";
import { useDemoMode } from "../../lib/session";
import { BankImportDetails } from "../../lib/types";
import { Button } from "../../components/Button";
import { Card, SectionTitle } from "../../components/Card";
import { Page } from "../../components/Page";
import { EmptyState, ErrorState, InlineError, SkeletonList } from "../../components/States";
import { useToast } from "../../components/Toast";
import { AccountsQueue } from "./AccountsQueue";
import { deskKeys, useImports } from "./queries";

export const MAX_STATEMENT_BYTES = 5 * 1024 * 1024;

export function statementFileError(file: { name: string; size: number }): string | null {
  if (!file.name.toLowerCase().endsWith(".csv")) return "Choose a .csv file exported from the bank.";
  if (file.size === 0) return "That file is empty.";
  if (file.size > MAX_STATEMENT_BYTES) return "The file is larger than 5 MB. Split the statement and upload each part.";
  return null;
}

export function PaymentsPage() {
  return (
    <Page title="Payments" width="wide">
      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_380px]">
        <section aria-labelledby="queue-heading">
          <h2 id="queue-heading" className="mb-3 text-[15px] font-semibold text-ink">
            Accounts queue
          </h2>
          <AccountsQueue />
        </section>
        <div className="space-y-6">
          <UploadCard />
          <RecentImports />
        </div>
      </div>
    </Page>
  );
}

function UploadCard() {
  const demoMode = useDemoMode();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const toast = useToast();
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [fileError, setFileError] = useState<string | null>(null);
  const [downloading, setDownloading] = useState(false);

  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData();
      form.append("file", file, file.name);
      return api.upload<BankImportDetails>("/api/desk/bank-imports", form);
    },
    onSuccess: (details) => {
      queryClient.invalidateQueries({ queryKey: ["desk"] });
      queryClient.setQueryData(deskKeys.importDetails(details.summary.id), details);
      navigate(`/desk/payments/imports/${details.summary.id}`);
    },
  });

  function choose(file: File | undefined) {
    if (!file || upload.isPending) return;
    const problem = statementFileError(file);
    setFileError(problem);
    if (!problem) upload.mutate(file);
  }

  function onDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    choose(event.dataTransfer.files[0]);
  }

  async function downloadSample() {
    setDownloading(true);
    try {
      await downloadFile("/api/desk/demo/bank-statement.csv", "demo-bank-statement.csv");
    } catch (error) {
      toast(error instanceof Error ? error.message : "Could not download the sample", "error");
    } finally {
      setDownloading(false);
    }
  }

  return (
    <Card className="p-5">
      <h2 className="text-[15px] font-semibold text-ink">Import bank statement</h2>
      <p className="mt-0.5 text-sm text-ink-soft">
        Payments that clearly match a flat are posted. Anything unclear goes to the queue for you to decide.
      </p>
      <div
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`mt-4 rounded-xl border-2 border-dashed px-4 py-6 text-center transition-colors ${
          dragging ? "border-accent bg-accent-soft" : "border-line-strong"
        }`}
      >
        <p className="text-sm text-ink-soft">Drop the CSV here, or</p>
        <Button className="mt-2" variant="secondary" busy={upload.isPending} onClick={() => input.current?.click()}>
          {upload.isPending ? "Matching payments…" : "Choose file"}
        </Button>
        <p className="mt-2 text-xs text-ink-faint">.csv up to 5 MB</p>
        <input
          ref={input}
          type="file"
          accept=".csv,text/csv"
          className="sr-only"
          tabIndex={-1}
          aria-hidden
          onChange={(event) => {
            choose(event.target.files?.[0]);
            event.target.value = "";
          }}
        />
      </div>
      {fileError && <p className="mt-2 text-sm text-danger">{fileError}</p>}
      <div className="mt-2">
        <InlineError error={upload.error} />
      </div>
      {demoMode && (
        <Button variant="ghost" size="sm" className="mt-2" busy={downloading} onClick={downloadSample}>
          Download sample statement
        </Button>
      )}
    </Card>
  );
}

function RecentImports() {
  const imports = useImports();
  return (
    <section aria-labelledby="imports-heading">
      <SectionTitle>
        <span id="imports-heading">Recent imports</span>
      </SectionTitle>
      {imports.isPending ? (
        <SkeletonList rows={2} />
      ) : imports.isError ? (
        <ErrorState error={imports.error} onRetry={() => imports.refetch()} />
      ) : imports.data.length === 0 ? (
        <EmptyState title="No statements imported yet" body="Upload the bank's CSV export to match payments to flats." />
      ) : (
        <ul className="divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
          {imports.data.slice(0, 8).map((summary) => (
            <li key={summary.id}>
              <Link
                to={`/desk/payments/imports/${summary.id}`}
                className="flex min-h-touch items-center justify-between gap-3 px-4 py-3 hover:bg-canvas"
              >
                <span className="min-w-0">
                  <span className="block truncate text-sm font-medium text-ink">{summary.fileName}</span>
                  <span className="block text-xs text-ink-faint">
                    {formatDateTime(summary.uploadedAt)} · {summary.uploadedBy}
                  </span>
                </span>
                <span className="whitespace-nowrap text-right text-xs tabular-nums text-ink-soft">
                  {summary.autoPosted} posted
                  <br />
                  {summary.suggested + summary.conflicts + summary.unmatched + summary.possibleDuplicates} queued
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
