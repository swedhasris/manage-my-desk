import React, { useEffect, useState, useRef } from "react";
import { cn } from "@/lib/utils";
import { motion } from "motion/react";

interface SLATimerProps {
  label: string;
  deadline: string;
  startTime?: string; // Total SLA duration reference
  metAt?: string;
  isPaused?: boolean;
  onHoldStart?: string;
  totalPausedTime?: number;
  waitUntil?: string | null;
}

/**
 * Safely parse any date value (ISO string, Firestore timestamp object, or Date)
 * into milliseconds. Returns NaN if unparseable.
 */
function toMs(val: any): number {
  if (!val) return NaN;
  // Firestore Timestamp object: { seconds: number, nanoseconds: number }
  if (typeof val === 'object' && val.seconds !== undefined) {
    return val.seconds * 1000 + (val.nanoseconds || 0) / 1_000_000;
  }
  // Firestore Timestamp with toDate()
  if (typeof val === 'object' && typeof val.toDate === 'function') {
    return val.toDate().getTime();
  }
  // Already a number (ms)
  if (typeof val === 'number') return val;
  // ISO string or any string Date can parse
  const ms = new Date(val).getTime();
  return ms;
}

export function SLATimer({
  label,
  deadline,
  startTime,
  metAt,
  isPaused = false,
  onHoldStart,
  totalPausedTime = 0,
  waitUntil,
}: SLATimerProps) {
  const [displayTime, setDisplayTime] = useState("");
  const [breachDuration, setBreachDuration] = useState("");
  const [status, setStatus] = useState<"waiting" | "met" | "breached" | "active" | "paused">("active");
  const [percentage, setPercentage] = useState(0);
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    // Clear any existing interval
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }

    // SLA already met — freeze the display
    if (metAt) {
      const metMs = toMs(metAt);
      if (!isNaN(metMs)) {
        setStatus("met");
        setDisplayTime("MET ✓");
        setBreachDuration("");
        setPercentage(100);
        return;
      }
    }

    // Resolution SLA: waiting for first response before starting
    if (waitUntil !== undefined && (waitUntil === null || waitUntil === "")) {
      setStatus("waiting");
      setDisplayTime("—");
      setBreachDuration("");
      return;
    }

    const deadlineMs = toMs(deadline);
    const startMs = startTime ? toMs(startTime) : (deadlineMs - 24 * 3_600_000); // Default to 24h if no start time
    
    if (isNaN(deadlineMs)) {
      setDisplayTime("--:--:--");
      return;
    }

    const tick = () => {
      const now = Date.now();
      let effectiveNow = now;
      
      if (isPaused && onHoldStart) {
        const holdMs = toMs(onHoldStart);
        if (!isNaN(holdMs)) effectiveNow = holdMs;
      }

      // Adjust for paused time
      const diff = deadlineMs - effectiveNow + (totalPausedTime || 0);
      const totalDuration = deadlineMs - (isNaN(startMs) ? deadlineMs - 24 * 3_600_000 : startMs);
      
      // Calculate percentage used: (elapsed / total) * 100
      const elapsed = effectiveNow - (isNaN(startMs) ? deadlineMs - 24 * 3_600_000 : startMs) - (totalPausedTime || 0);
      const calculatedPercentage = totalDuration > 0 ? Math.min(Math.max((elapsed / totalDuration) * 100, 0), 100) : 0;

      if (diff <= 0) {
        // === BREACHED: Clamp display to 00:00:00 ===
        setStatus("breached");
        setDisplayTime("00:00:00");
        setPercentage(100);

        // Calculate how long ago the breach occurred (for context only)
        const overdue = Math.abs(diff);
        if (overdue >= 3_600_000) {
          const h = Math.floor(overdue / 3_600_000);
          const m = Math.floor((overdue % 3_600_000) / 60_000);
          setBreachDuration(`${h}h ${m}m overdue`);
        } else if (overdue >= 60_000) {
          const m = Math.floor(overdue / 60_000);
          setBreachDuration(`${m}m overdue`);
        } else {
          setBreachDuration("just breached");
        }

        // Stop the interval — no need to keep ticking once breached
        if (timerRef.current) {
          clearInterval(timerRef.current);
          timerRef.current = null;
        }
      } else {
        setStatus(isPaused ? "paused" : "active");
        setBreachDuration("");
        const h = Math.floor(diff / 3_600_000);
        const m = Math.floor((diff % 3_600_000) / 60_000);
        const s = Math.floor((diff % 60_000) / 1_000);
        setDisplayTime(
          `${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
        );
        setPercentage(calculatedPercentage);
      }
    };

    tick();
    timerRef.current = setInterval(tick, 1000);
    return () => {
      if (timerRef.current) {
        clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [deadline, startTime, metAt, isPaused, onHoldStart, totalPausedTime, waitUntil]);

  // Color Escalation Logic
  const getEscalationColor = () => {
    if (status === "met") return "bg-emerald-500";
    if (status === "breached") return "bg-red-600";
    if (status === "paused") return "bg-amber-400";
    
    if (percentage >= 75) return "bg-red-500";
    if (percentage >= 50) return "bg-yellow-500";
    return "bg-sn-green"; // Healthy (< 50%)
  };

  const getEscalationTextColor = () => {
    if (status === "met") return "text-emerald-600";
    if (status === "breached") return "text-red-600";
    if (status === "paused") return "text-amber-600";
    if (status === "waiting") return "text-gray-400";
    
    if (percentage >= 75) return "text-red-600";
    if (percentage >= 50) return "text-yellow-600";
    return "text-blue-600"; // Default
  };

  return (
    <div className="flex flex-col gap-0.5 min-w-[110px] group">
      <div className="flex items-center justify-between gap-1">
        <span className="text-[9px] uppercase text-muted-foreground font-black leading-none tracking-wider">
          {label}
        </span>
        {status === "paused" ? (
          <span className="text-[8px] font-black text-amber-600 uppercase animate-pulse">
            PAUSED
          </span>
        ) : status === "breached" ? (
          <span className="text-[8px] font-black text-red-600 uppercase animate-pulse">
            BREACHED
          </span>
        ) : null}
      </div>
      
      <div className="flex items-baseline gap-1.5">
        <span
          className={cn(
            "text-[13px] font-mono font-black leading-tight tracking-tight",
            getEscalationTextColor()
          )}
        >
          {displayTime}
        </span>
        {status === "breached" && breachDuration ? (
          <span className="text-[8px] font-bold text-red-500/70 italic">
            {breachDuration}
          </span>
        ) : status !== "met" && status !== "waiting" ? (
          <span className="text-[9px] font-bold text-muted-foreground/60">
            {Math.round(percentage)}%
          </span>
        ) : null}
      </div>

      {/* Progress bar with Escalation Colors */}
      <div className="w-full h-1.5 bg-muted rounded-full overflow-hidden mt-0.5 shadow-inner">
        <motion.div
          layout
          className={cn("h-full transition-all duration-1000", getEscalationColor())}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
