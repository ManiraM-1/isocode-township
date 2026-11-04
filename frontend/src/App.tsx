import { ReactNode } from "react";
import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import { homePathFor, useMe } from "./lib/session";
import { Role } from "./lib/types";
import { ErrorState, Skeleton } from "./components/States";
import { LoginPage } from "./features/auth/LoginPage";
import { ResidentLayout } from "./features/resident/ResidentLayout";
import { ResidentHome } from "./features/resident/ResidentHome";
import { BillsPage } from "./features/resident/BillsPage";
import { PayPage } from "./features/resident/PayPage";
import { ReceiptPage } from "./features/resident/ReceiptPage";
import { ComplaintsPage } from "./features/resident/ComplaintsPage";
import { NewComplaintPage } from "./features/resident/NewComplaintPage";
import { ComplaintDetailPage } from "./features/resident/ComplaintDetailPage";
import { DeskLayout } from "./features/desk/DeskLayout";
import { ComplaintBoard } from "./features/desk/ComplaintBoard";
import { DeskComplaintDetail } from "./features/desk/DeskComplaintDetail";

export function App() {
  return (
    <Routes>
      <Route path="/login" element={<SignedOutOnly><LoginPage /></SignedOutOnly>} />
      <Route path="/" element={<RoleHome />} />
      <Route element={<RequireRole role="RESIDENT"><ResidentLayout /></RequireRole>}>
        <Route path="/home" element={<ResidentHome />} />
        <Route path="/bills" element={<BillsPage />} />
        <Route path="/bills/pay" element={<PayPage />} />
        <Route path="/receipts/:receiptNo" element={<ReceiptPage />} />
        <Route path="/complaints" element={<ComplaintsPage />} />
        <Route path="/complaints/new" element={<NewComplaintPage />} />
        <Route path="/complaints/:complaintId" element={<ComplaintDetailPage />} />
      </Route>
      <Route element={<RequireRole role="HELPDESK"><DeskLayout /></RequireRole>}>
        <Route path="/desk/complaints" element={<ComplaintBoard />} />
        <Route path="/desk/complaints/:complaintId" element={<DeskComplaintDetail />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

function FullPageLoading() {
  return (
    <div className="mx-auto max-w-2xl space-y-4 px-4 pt-10" role="status" aria-label="Loading">
      <Skeleton className="h-8 w-40" />
      <Skeleton className="h-40 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  );
}

function RoleHome() {
  const me = useMe();
  if (me.isPending) return <FullPageLoading />;
  if (me.isError) return <div className="mx-auto max-w-md px-4 pt-16"><ErrorState error={me.error} onRetry={() => me.refetch()} /></div>;
  return <Navigate to={me.data ? homePathFor(me.data) : "/login"} replace />;
}

function SignedOutOnly({ children }: { children: ReactNode }) {
  const me = useMe();
  if (me.isPending) return <FullPageLoading />;
  if (me.data) return <Navigate to={homePathFor(me.data)} replace />;
  return <>{children}</>;
}

function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const me = useMe();
  const location = useLocation();
  if (me.isPending) return <FullPageLoading />;
  if (me.isError) return <div className="mx-auto max-w-md px-4 pt-16"><ErrorState error={me.error} onRetry={() => me.refetch()} /></div>;
  if (!me.data) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  if (me.data.role !== role) return <Navigate to={homePathFor(me.data)} replace />;
  return <>{children}</>;
}
