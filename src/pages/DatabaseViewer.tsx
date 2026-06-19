import React, { useState, useEffect } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { Download, RefreshCw, Database, Search, ChevronLeft, ChevronRight } from 'lucide-react';

export function DatabaseViewer() {
  const { profile } = useAuth();
  const [tables, setTables] = useState<any[]>([]);
  const [selectedTable, setSelectedTable] = useState<string | null>(null);
  const [tableData, setTableData] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  
  // Pagination and Filtering
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(50);
  const [search, setSearch] = useState('');
  const [sortBy, setSortBy] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'ASC' | 'DESC'>('ASC');

  const isAdmin = profile?.role === 'ultra_super_admin' || profile?.role === 'super_admin';

  useEffect(() => {
    if (isAdmin) {
      fetchTables();
    }
  }, [isAdmin]);

  useEffect(() => {
    if (selectedTable) {
      fetchTableData();
    }
  }, [selectedTable, page, size, search, sortBy, sortDir]);

  const fetchTables = async () => {
    try {
      setLoading(true);
      const res = await fetch('/api/admin/database/tables');
      if (res.ok) {
        const data = await res.json();
        setTables(data);
      }
    } catch (err) {
      console.error('Failed to fetch tables', err);
    } finally {
      setLoading(false);
    }
  };

  const fetchTableData = async () => {
    try {
      setLoading(true);
      const params = new URLSearchParams({
        page: page.toString(),
        size: size.toString(),
        sortDirection: sortDir
      });
      if (search) params.append('search', search);
      if (sortBy) params.append('sortBy', sortBy);

      const res = await fetch(`/api/admin/database/tables/${selectedTable}?${params.toString()}`);
      if (res.ok) {
        const data = await res.json();
        setTableData(data);
      }
    } catch (err) {
      console.error('Failed to fetch table data', err);
    } finally {
      setLoading(false);
    }
  };

  const handleExportCSV = () => {
    if (!tableData || !tableData.rows || tableData.rows.length === 0) return;
    
    const columns = tableData.columns.map((c: any) => c.column_name || c.COLUMN_NAME);
    const csvRows = [];
    
    // Headers
    csvRows.push(columns.join(','));
    
    // Data
    for (const row of tableData.rows) {
      const values = columns.map((col: string) => {
        const val = row[col];
        if (val === null || val === undefined) return '""';
        return `"${String(val).replace(/"/g, '""')}"`;
      });
      csvRows.push(values.join(','));
    }
    
    const blob = new Blob([csvRows.join('\n')], { type: 'text/csv' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${selectedTable}_export.csv`;
    a.click();
    window.URL.revokeObjectURL(url);
  };

  const handleSort = (column: string) => {
    if (sortBy === column) {
      setSortDir(sortDir === 'ASC' ? 'DESC' : 'ASC');
    } else {
      setSortBy(column);
      setSortDir('ASC');
    }
    setPage(1); // reset to page 1 on sort
  };

  if (!isAdmin) {
    return (
      <div className="flex items-center justify-center h-full p-8 text-center text-rose-500">
        <h2>Access Denied: You must be an Ultra Super Admin or Super Admin to view the database.</h2>
      </div>
    );
  }

  return (
    <div className="flex h-full bg-background overflow-hidden p-6 gap-6">
      {/* Sidebar for Tables */}
      <div className="w-64 bg-card border border-border rounded-xl flex flex-col shadow-sm">
        <div className="p-4 border-b border-border flex justify-between items-center bg-muted/20">
          <h2 className="font-semibold text-foreground flex items-center gap-2">
            <Database className="w-4 h-4 text-blue-500" />
            Tables
          </h2>
          <button onClick={fetchTables} className="p-1.5 hover:bg-muted rounded text-muted-foreground transition-colors">
            <RefreshCw className={`w-3.5 h-3.5 ${loading && !selectedTable ? 'animate-spin' : ''}`} />
          </button>
        </div>
        <div className="flex-1 overflow-y-auto p-2 space-y-1">
          {tables.map(t => (
            <button
              key={t.name}
              onClick={() => {
                setSelectedTable(t.name);
                setPage(1);
                setSearch('');
                setSortBy(null);
              }}
              className={`w-full text-left px-3 py-2 rounded-lg text-sm flex justify-between items-center transition-colors ${
                selectedTable === t.name 
                  ? 'bg-blue-500/10 text-blue-600 font-medium dark:text-blue-400' 
                  : 'hover:bg-muted text-muted-foreground'
              }`}
            >
              <span className="truncate">{t.name}</span>
              <span className="text-xs opacity-60 bg-muted px-1.5 py-0.5 rounded-full">{t.rowCount}</span>
            </button>
          ))}
        </div>
      </div>

      {/* Main Data View */}
      <div className="flex-1 bg-card border border-border rounded-xl flex flex-col shadow-sm overflow-hidden">
        {selectedTable && tableData ? (
          <>
            <div className="p-4 border-b border-border bg-muted/20 flex flex-wrap gap-4 justify-between items-center">
              <div>
                <h1 className="text-xl font-semibold flex items-center gap-2">
                  <Database className="w-5 h-5 text-blue-500" />
                  {selectedTable}
                </h1>
                <p className="text-sm text-muted-foreground mt-1">
                  Showing {tableData.rows.length} rows (Total: {tableData.totalElements})
                </p>
              </div>
              <div className="flex items-center gap-3">
                <div className="relative">
                  <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground" />
                  <input
                    type="text"
                    placeholder="Search all columns..."
                    value={search}
                    onChange={(e) => {
                      setSearch(e.target.value);
                      setPage(1);
                    }}
                    className="pl-9 pr-4 py-2 bg-background border border-border rounded-lg text-sm w-64 focus:ring-2 focus:ring-blue-500 outline-none transition-all"
                  />
                </div>
                <button
                  onClick={handleExportCSV}
                  className="flex items-center gap-2 px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg text-sm font-medium transition-colors"
                >
                  <Download className="w-4 h-4" />
                  Export CSV
                </button>
              </div>
            </div>

            <div className="flex-1 overflow-auto">
              <table className="w-full text-left whitespace-nowrap">
                <thead className="bg-muted/50 sticky top-0 z-10 backdrop-blur-sm">
                  <tr>
                    {tableData.columns.map((col: any) => {
                      const colName = col.column_name || col.COLUMN_NAME;
                      return (
                        <th 
                          key={colName} 
                          className="px-4 py-3 text-xs font-semibold text-muted-foreground uppercase tracking-wider border-b border-border cursor-pointer hover:bg-muted transition-colors select-none"
                          onClick={() => handleSort(colName)}
                        >
                          <div className="flex items-center gap-2">
                            {colName}
                            {sortBy === colName && (
                              <span className="text-blue-500">{sortDir === 'ASC' ? '↑' : '↓'}</span>
                            )}
                          </div>
                        </th>
                      );
                    })}
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/50">
                  {tableData.rows.length === 0 ? (
                    <tr>
                      <td colSpan={tableData.columns.length} className="px-4 py-8 text-center text-muted-foreground">
                        No records found
                      </td>
                    </tr>
                  ) : (
                    tableData.rows.map((row: any, i: number) => (
                      <tr key={i} className="hover:bg-muted/30 transition-colors">
                        {tableData.columns.map((col: any) => {
                          const colName = col.column_name || col.COLUMN_NAME;
                          const val = row[colName];
                          return (
                            <td key={colName} className="px-4 py-2 text-sm text-foreground max-w-[200px] truncate" title={String(val)}>
                              {val === null ? <span className="text-muted-foreground italic">null</span> : String(val)}
                            </td>
                          );
                        })}
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            </div>

            {/* Pagination */}
            <div className="p-4 border-t border-border bg-muted/10 flex justify-between items-center">
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <span>Rows per page:</span>
                <select 
                  value={size} 
                  onChange={(e) => {
                    setSize(Number(e.target.value));
                    setPage(1);
                  }}
                  className="bg-background border border-border rounded px-2 py-1 outline-none focus:ring-1 focus:ring-blue-500"
                >
                  <option value="50">50</option>
                  <option value="100">100</option>
                  <option value="500">500</option>
                </select>
              </div>
              
              <div className="flex items-center gap-4">
                <span className="text-sm text-muted-foreground">
                  Page {tableData.currentPage} of {tableData.totalPages || 1}
                </span>
                <div className="flex gap-1">
                  <button
                    onClick={() => setPage(p => Math.max(1, p - 1))}
                    disabled={page === 1}
                    className="p-1.5 rounded border border-border hover:bg-muted disabled:opacity-50 transition-colors"
                  >
                    <ChevronLeft className="w-4 h-4" />
                  </button>
                  <button
                    onClick={() => setPage(p => Math.min(tableData.totalPages, p + 1))}
                    disabled={page >= tableData.totalPages}
                    className="p-1.5 rounded border border-border hover:bg-muted disabled:opacity-50 transition-colors"
                  >
                    <ChevronRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-muted-foreground">
            <Database className="w-16 h-16 mb-4 opacity-20" />
            <p className="text-lg font-medium">Database Management</p>
            <p className="text-sm">Select a table from the sidebar to view its contents.</p>
          </div>
        )}
      </div>
    </div>
  );
}
