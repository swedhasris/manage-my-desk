import React, { useEffect, useState } from "react";
import { collection, onSnapshot, query } from "firebase/firestore";
import { db } from "../lib/firebase";
import { useAuth } from "../contexts/AuthContext";
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell } from "recharts";
import { RefreshCw, LayoutGrid } from "lucide-react";
import { Link } from "react-router-dom";
import { cn, formatDate } from "../lib/utils";

function toMs(val: any): number {
  if (!val) return NaN;
  if (typeof val === 'object' && val.seconds !== undefined) return val.seconds * 1000 + (val.nanoseconds || 0) / 1_000_000;
  if (typeof val === 'object' && typeof val.toDate === 'function') return val.toDate().getTime();
  if (typeof val === 'number') return val;
  return new Date(val).getTime();
}

function SLATimer({ deadline, metAt, isPaused, onHoldStart, totalPausedTime = 0, label, waitUntil }: { deadline: string, metAt?: string, isPaused?: boolean, onHoldStart?: string, totalPausedTime?: number, label: string, waitUntil?: string | null }) {
  const [displayTime, setDisplayTime] = useState("");
  const [status, setStatus] = useState<"waiting" | "met" | "breached" | "active" | "paused">("active");

  useEffect(() => {
    if (metAt) {
      const metMs = toMs(metAt);
      if (!isNaN(metMs)) { setStatus("met"); setDisplayTime("MET"); return; }
    }
    if (waitUntil !== undefined && (waitUntil === null || waitUntil === "")) {
      setStatus("waiting"); setDisplayTime("—"); return;
    }
    const deadlineMs = toMs(deadline);
    if (isNaN(deadlineMs)) { setDisplayTime("--:--:--"); return; }

    const tick = () => {
      const now = Date.now();
      let effectiveNow = now;
      if (isPaused && onHoldStart) {
        const holdMs = toMs(onHoldStart);
        if (!isNaN(holdMs)) effectiveNow = holdMs;
      }
      const diff = deadlineMs - effectiveNow + (totalPausedTime || 0);

      if (diff <= 0) {
        setStatus("breached");
        const over = Math.abs(diff);
        const h = Math.floor(over / 3600000), m = Math.floor((over % 3600000) / 60000), s = Math.floor((over % 60000) / 1000);
        setDisplayTime(`-${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`);
      } else {
        setStatus(isPaused ? "paused" : "active");
        const h = Math.floor(diff / 3600000), m = Math.floor((diff % 3600000) / 60000), s = Math.floor((diff % 60000) / 1000);
        setDisplayTime(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`);
      }
    };

    tick();
    const timer = setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [deadline, metAt, isPaused, onHoldStart, totalPausedTime, waitUntil]);

  return (
    <div className="flex flex-col gap-0.5 min-w-[80px]">
      <span className="text-[9px] uppercase text-muted-foreground font-bold leading-none">{label}</span>
      <span className={cn(
        "text-[10px] font-mono font-bold leading-none",
        status === "met" ? "text-green-600" :
          status === "breached" ? "text-red-600" :
            status === "waiting" ? "text-gray-400" :
              status === "paused" ? "text-orange-500" : "text-blue-600"
      )}>
        {displayTime}
      </span>
    </div>
  );
}

const PRIORITY_COLORS: Record<string, string> = {
  "1 - Critical": "#e74c3c",
  "2 - High": "#f39c12",
  "3 - Moderate": "#27ae60",
  "4 - Low": "#3498db",
};

export function Dashboard() {
  const { profile } = useAuth();
  const [tickets, setTickets] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [lastRefresh, setLastRefresh] = useState(new Date());

  const [users, setUsers] = useState<any[]>([]);

  useEffect(() => {
    const unsubTickets = onSnapshot(query(collection(db, "tickets")), snap => {
      setTickets(snap.docs.map(d => ({ id: d.id, ...d.data() })));
      setLoading(false);
      setLastRefresh(new Date());
    });
    const unsubUsers = onSnapshot(query(collection(db, "users")), snap => {
      setUsers(snap.docs.map(d => ({ id: d.id, ...d.data() })));
    });
    return () => { unsubTickets(); unsubUsers(); };
  }, []);

  const now = Date.now();
  const thirtyDaysAgo = now - 30 * 24 * 3600 * 1000;
  const sevenDaysAgo = now - 7 * 24 * 3600 * 1000;

  const getTs = (t: any) => {
    const c = t.createdAt;
    if (!c) return 0;
    if (c?.seconds) return c.seconds * 1000;
    if (typeof c === "string") return new Date(c).getTime();
    return 0;
  };

  const open = tickets.filter(t => !["Resolved", "Closed", "Canceled"].includes(t.status ?? ""));
  const closed = tickets.filter(t => ["Resolved", "Closed"].includes(t.status ?? ""));

  // Stats
  const criticalOpen = open.filter(t => (t.priority ?? "").includes("Critical")).length;
  const unassigned = open.filter(t => !t.assignedTo).length;
  const overdue = open.filter(t => t.resolutionDeadline && new Date(t.resolutionDeadline).getTime() < now).length;
  const openCount = open.length;
  const stale7 = open.filter(t => getTs(t) < sevenDaysAgo).length;
  const older30 = open.filter(t => getTs(t) < thirtyDaysAgo).length;

  // Group open by priority for bar chart
  const priorityGroups = ["1 - Critical", "2 - High", "3 - Moderate", "4 - Low"].map(p => ({
    name: p.replace(" - ", "\n"),
    label: p,
    count: open.filter(t => t.priority === p).length,
  }));

  // Group older-30 by priority
  const older30Groups = ["1 - Critical", "2 - High", "3 - Moderate", "4 - Low"].map(p => ({
    name: p.replace(" - ", "\n"),
    label: p,
    count: open.filter(t => t.priority === p && getTs(t) < thirtyDaysAgo).length,
  }));

  // Recent tickets table
  const recent = [...tickets]
    .sort((a, b) => getTs(b) - getTs(a))
    .slice(0, 8);

  const statCards = [
    { label: "Critical Open Incidents", value: criticalOpen, color: "text-red-500 font-bold", link: "/tickets?filter=critical_open" },
    { label: "Unassigned Incidents", value: unassigned, color: "text-foreground", link: "/tickets?filter=unassigned" },
    { label: "Overdue Incidents", value: overdue, color: "text-red-600 font-black", link: "/tickets?filter=overdue" },
    { label: "Open Incidents", value: openCount, color: "text-foreground", link: "/tickets?filter=open" },
    { label: "Incidents not updated for 7 days", value: stale7, color: "text-foreground", link: "/tickets?filter=stale_7" },
    { label: "Open Incidents older than 30 Days", value: older30, color: "text-foreground", link: "/tickets?filter=older_30" },
  ];

  return (
    <div className="space-y-6 max-w-7xl mx-auto">

      {/* Header */}
      <div className="flex items-center justify-between pb-3 border-b border-border">
        <h1 className="text-lg font-bold text-foreground">Incident Overview</h1>
        <div className="flex items-center gap-2">
          <button
            onClick={() => setLastRefresh(new Date())}
            className="flex items-center gap-1.5 px-3 py-1.5 border border-border rounded text-xs font-medium hover:bg-muted transition-colors"
          >
            <RefreshCw className="w-3.5 h-3.5" />
            Refresh
          </button>
          <button className="flex items-center gap-1.5 px-3 py-1.5 border border-border rounded text-xs font-medium hover:bg-muted transition-colors">
            <LayoutGrid className="w-3.5 h-3.5" />
            Change Layout
          </button>
          <span className="text-[10px] text-muted-foreground">
            Last updated: {lastRefresh.toLocaleTimeString()}
          </span>
        </div>
      </div>

      {/* 6 Stat Cards — 3 columns × 2 rows */}
      <div className="bg-white border border-border rounded-lg shadow-sm overflow-hidden">
        <div className="grid grid-cols-3 divide-x divide-border">
          {statCards.slice(0, 3).map((s, i) => (
            <Link key={i} to={s.link} className="p-6 text-center hover:bg-muted/10 transition-colors group">
              <div className="text-sm font-semibold text-foreground mb-2">{s.label}</div>
              <div className={`text-5xl font-light ${s.color} group-hover:scale-105 transition-transform inline-block`}>
                {loading ? "—" : s.value}
              </div>
            </Link>
          ))}
        </div>
        <div className="grid grid-cols-3 divide-x divide-border border-t border-border">
          {statCards.slice(3, 6).map((s, i) => (
            <Link key={i} to={s.link} className="p-6 text-center hover:bg-muted/10 transition-colors group">
              <div className="text-sm font-semibold text-foreground mb-2">{s.label}</div>
              <div className={`text-5xl font-light ${s.color} group-hover:scale-105 transition-transform inline-block`}>
                {loading ? "—" : s.value}
              </div>
            </Link>
          ))}
        </div>
      </div>

      {/* Two Bar Charts */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* Open Incidents Grouped by Priority */}
        <div className="bg-white border border-border rounded-lg shadow-sm p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-bold text-foreground">Open Incidents — Grouped by Priority</h3>
          </div>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={priorityGroups} layout="vertical" margin={{ left: 10, right: 20 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" fontSize={11} allowDecimals={false} />
                <YAxis type="category" dataKey="label" fontSize={10} width={90} />
                <Tooltip
                  formatter={(v: any) => [v, "Tickets"]}
                  contentStyle={{ fontSize: 11, borderRadius: 6 }}
                />
                <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                  {priorityGroups.map((entry, i) => (
                    <Cell key={i} fill={PRIORITY_COLORS[entry.label] || "#64748b"} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
          {/* Legend */}
          <div className="flex flex-wrap gap-3 mt-3">
            {Object.entries(PRIORITY_COLORS).map(([label, color]) => (
              <div key={label} className="flex items-center gap-1.5 text-[10px]">
                <div className="w-3 h-3 rounded-sm" style={{ background: color }} />
                <span>{label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Open Incidents Older than 30 Days */}
        <div className="bg-white border border-border rounded-lg shadow-sm p-5">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-bold text-foreground">Open Incidents older than 30 Days — Grouped</h3>
          </div>
          <div className="h-56">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={older30Groups} layout="vertical" margin={{ left: 10, right: 20 }}>
                <CartesianGrid strokeDasharray="3 3" horizontal={false} />
                <XAxis type="number" fontSize={11} allowDecimals={false} />
                <YAxis type="category" dataKey="label" fontSize={10} width={90} />
                <Tooltip
                  formatter={(v: any) => [v, "Tickets"]}
                  contentStyle={{ fontSize: 11, borderRadius: 6 }}
                />
                <Bar dataKey="count" radius={[0, 4, 4, 0]}>
                  {older30Groups.map((entry, i) => (
                    <Cell key={i} fill={PRIORITY_COLORS[entry.label] || "#64748b"} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
          <div className="flex flex-wrap gap-3 mt-3">
            {Object.entries(PRIORITY_COLORS).map(([label, color]) => (
              <div key={label} className="flex items-center gap-1.5 text-[10px]">
                <div className="w-3 h-3 rounded-sm" style={{ background: color }} />
                <span>{label}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      <div className="bg-white border border-border rounded-lg shadow-sm overflow-hidden">
        <div className="p-4 border-b border-border flex items-center justify-between bg-muted/10">
          <h3 className="text-sm font-bold">Recent Incidents</h3>
          <Link to="/tickets" className="text-xs text-blue-600 hover:underline font-medium uppercase tracking-widest">View All Incidents</Link>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead>
              <tr className="bg-muted/30 border-b border-border text-[10px] font-bold uppercase text-muted-foreground tracking-wide">
                <th className="p-3">Number</th>
                <th className="p-3">Short Description</th>
                <th className="p-3">Priority</th>
                <th className="p-3">State</th>
                <th className="p-3">Category</th>
                <th className="p-3">Assigned To</th>
                <th className="p-3">SLA Status</th>
                <th className="p-3">Created</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={8} className="p-8 text-center text-muted-foreground text-sm">Loading...</td></tr>
              ) : recent.length === 0 ? (
                <tr><td colSpan={8} className="p-8 text-center text-muted-foreground text-sm">No incidents found.</td></tr>
              ) : recent.map(t => {
                const p = t.priority ?? "4 - Low";
                const pColor = p.includes("Critical") ? "bg-red-600 text-white"
                  : p.includes("High") ? "bg-red-100 text-red-700"
                    : p.includes("Moderate") ? "bg-orange-100 text-orange-700"
                      : "bg-blue-100 text-blue-700";

                const isPaused = t.status === "On Hold" || t.status === "Waiting for Customer";

                return (
                  <tr key={t.id} className="border-b border-border hover:bg-muted/5 transition-colors">
                    <td className="p-3">
                      <Link to={`/tickets/${t.id}`} className="font-mono text-[11px] font-bold text-blue-600 hover:underline">
                        {t.number ?? t.id.slice(0, 8)}
                      </Link>
                    </td>
                    <td className="p-3 text-[11px] font-medium max-w-[200px] truncate">{t.title ?? "—"}</td>
                    <td className="p-3">
                      <span className={`text-[9px] font-bold px-1.5 py-0.5 rounded uppercase ${pColor}`}>{p}</span>
                    </td>
                    <td className="p-3">
                      <span className="text-[11px] font-medium">{t.status ?? "New"}</span>
                    </td>
                    <td className="p-3 text-[11px] text-muted-foreground">{t.category ?? "—"}</td>
                    <td className="p-3 text-[11px] font-medium">
                      {t.assignedToName || users.find(u => u.id === t.assignedTo)?.name || t.assignedTo || "Unassigned"}
                    </td>
                    <td className="p-3">
                      <div className="flex flex-col gap-1.5">
                        <SLATimer
                          label="Resp"
                          deadline={t.responseDeadline}
                          metAt={t.firstResponseAt}
                          isPaused={isPaused}
                          onHoldStart={t.onHoldStart}
                          totalPausedTime={t.totalPausedTime}
                        />
                        <SLATimer
                          label="Res"
                          deadline={t.resolutionDeadline}
                          metAt={t.resolvedAt}
                          isPaused={isPaused}
                          onHoldStart={t.onHoldStart}
                          totalPausedTime={t.totalPausedTime}
                          waitUntil={t.firstResponseAt ?? null}
                        />
                      </div>
                    </td>
                    <td className="p-3 text-[11px] text-muted-foreground">
                      {formatDate(t.createdAt)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

    </div>
  );
}
