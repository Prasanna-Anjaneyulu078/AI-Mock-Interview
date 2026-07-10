import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { BsClipboardData, BsGraphUp, BsLightbulb, BsCheckCircle, BsExclamationTriangle } from 'react-icons/bs';
import { MdDashboard, MdTrendingUp, MdSchool, MdHistory, MdInsights } from 'react-icons/md';
import { getSummary, getSkills, getProgress } from '../../services/analyticsService.js';
import toast from 'react-hot-toast';
import './index.css';

function StatCard({ label, value, suffix, accent }) {
  return (
    <div className="dash-stat-card">
      <span className="dash-stat-label">{label}</span>
      <span className="dash-stat-value" style={accent ? { color: accent } : undefined}>
        {value}
        {suffix && <span className="dash-stat-suffix">{suffix}</span>}
      </span>
    </div>
  );
}

function SkillPills({ items, empty }) {
  if (!items || items.length === 0) {
    return <p className="dash-pill-empty">{empty}</p>;
  }
  return (
    <div className="dash-pills">
      {items.map((s, i) => (
        <span key={`${s}-${i}`} className="dash-pill">
          {s}
        </span>
      ))}
    </div>
  );
}

function DashboardPage() {
  const navigate = useNavigate();

  const [summary, setSummary] = useState(null);
  const [skills, setSkills] = useState(null);
  const [progress, setProgress] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    loadDashboard();
  }, []);

  const loadDashboard = async () => {
    setLoading(true);
    setError(false);
    try {
      const [s, sk, p] = await Promise.all([getSummary(), getSkills(), getProgress()]);
      setSummary(s);
      setSkills(sk);
      setProgress(p);
    } catch (err) {
      console.error('Failed to load dashboard', err);
      setError(true);
      toast.error('Failed to load dashboard');
    } finally {
      setLoading(false);
    }
  };

  const totalInterviews = summary?.totalInterviews ?? 0;
  const hasData = !loading && !error && totalInterviews > 0;

  const trendData = (progress?.performanceTrend || [])
    .map((p) => ({ label: p.label, score: p.score == null ? 0 : p.score }))
    .filter((p) => p.label); // keep only dated points

  const skillTrendData = (progress?.skillGrowthTrend || []).filter((p) => p.label);

  const scoreColor = (score) => {
    if (score == null) return '#64748b';
    if (score >= 80) return '#16a34a';
    if (score >= 50) return '#2563eb';
    return '#dc2626';
  };

  return (
    <div className="dashboard-page">
      <div className="dashboard-container">
        <div className="dashboard-header-row">
          <div className="dashboard-header-left">
            <h1 className="dashboard-heading">
              <MdDashboard className="dashboard-heading-icon" />
              Performance Dashboard
            </h1>
            {summary && (
              <span className="dashboard-count-badge">
                {totalInterviews} interview{totalInterviews !== 1 ? 's' : ''}
              </span>
            )}
          </div>
          {!loading && !error && (
            <button className="dashboard-refresh-btn" onClick={loadDashboard}>
              Refresh
            </button>
          )}
        </div>

        {/* Loading state */}
        {loading && (
          <div className="dashboard-loading-state">
            <div className="spinner-border spinner-border-sm" role="status" />
            <p className="dashboard-loading-text">Loading your performance dashboard…</p>
          </div>
        )}

        {/* Error state */}
        {!loading && error && (
          <div className="dashboard-error-state">
            <BsExclamationTriangle className="dashboard-error-icon" />
            <h3 className="dashboard-error-heading">Couldn’t load the dashboard</h3>
            <p className="dashboard-error-desc">
              Something went wrong while fetching your analytics.
            </p>
            <button className="dashboard-start-btn" onClick={loadDashboard}>
              Try Again
            </button>
          </div>
        )}

        {/* Empty state */}
        {!loading && !error && !hasData && (
          <div className="dashboard-empty-state">
            <BsClipboardData className="dashboard-empty-icon" />
            <h3 className="dashboard-empty-heading">No interviews yet</h3>
            <p className="dashboard-empty-desc">
              Complete an interview to start tracking your scores, skills, and progress.
            </p>
            <Link to="/setup" className="dashboard-start-btn">
              Start Your First Interview
            </Link>
          </div>
        )}

        {/* Content */}
        {hasData && (
          <>
            {/* 1. Overview Cards */}
            <section className="dashboard-section">
              <h2 className="dashboard-section-title">
                <MdInsights className="dashboard-section-icon" />
                Overview
              </h2>
              <div className="dash-overview-cards">
                <StatCard label="Total Interviews" value={summary.totalInterviews} />
                <StatCard
                  label="Average Score"
                  value={summary.averageScore?.toFixed(1)}
                  accent={scoreColor(summary.averageScore)}
                />
                <StatCard
                  label="Best Score"
                  value={summary.bestScore?.toFixed(1)}
                  accent={scoreColor(summary.bestScore)}
                />
                <StatCard
                  label="Completion Rate"
                  value={summary.completionRate?.toFixed(0)}
                  suffix="%"
                  accent="#2563eb"
                />
              </div>
            </section>

            {/* 2. Skill Insights */}
            <section className="dashboard-section">
              <h2 className="dashboard-section-title">
                <MdSchool className="dashboard-section-icon" />
                Skill Insights
              </h2>
              <div className="dash-skill-cols">
                <div className="dash-skill-col">
                  <h3 className="dash-skill-col-title dash-skill-col-title--strong">
                    <BsCheckCircle className="dash-skill-col-icon" />
                    Strong Skills
                  </h3>
                  <SkillPills items={skills.strongSkills} empty="No strong skills recorded yet." />
                </div>
                <div className="dash-skill-col">
                  <h3 className="dash-skill-col-title dash-skill-col-title--weak">
                    <BsExclamationTriangle className="dash-skill-col-icon" />
                    Weak Skills
                  </h3>
                  <SkillPills items={skills.weakSkills} empty="No weak skills recorded yet." />
                </div>
                <div className="dash-skill-col">
                  <h3 className="dash-skill-col-title dash-skill-col-title--improve">
                    <BsLightbulb className="dash-skill-col-icon" />
                    Improvement Areas
                  </h3>
                  <SkillPills items={skills.improvementAreas} empty="No suggestions yet." />
                </div>
              </div>
            </section>

            {/* 3. Performance Trends */}
            <section className="dashboard-section">
              <h2 className="dashboard-section-title">
                <MdTrendingUp className="dashboard-section-icon" />
                Performance Trends
              </h2>
              <div className="dash-charts">
                <div className="dash-chart-card">
                  <h3 className="dash-chart-title">Score Progression</h3>
                  {trendData.length > 0 ? (
                    <ResponsiveContainer width="100%" height={300}>
                      <LineChart data={trendData} margin={{ top: 10, right: 24, left: 0, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                        <YAxis domain={[0, 100]} tick={{ fontSize: 12 }} />
                        <Tooltip />
                        <Legend />
                        <Line
                          type="monotone"
                          dataKey="score"
                          name="Score"
                          stroke="#2563eb"
                          strokeWidth={2}
                          dot={{ r: 4 }}
                          activeDot={{ r: 6 }}
                        />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <p className="dash-chart-empty">Not enough data to plot a trend yet.</p>
                  )}
                </div>

                <div className="dash-chart-card">
                  <h3 className="dash-chart-title">Skill Growth</h3>
                  {skillTrendData.length > 0 ? (
                    <ResponsiveContainer width="100%" height={300}>
                      <LineChart data={skillTrendData} margin={{ top: 10, right: 24, left: 0, bottom: 0 }}>
                        <CartesianGrid strokeDasharray="3 3" />
                        <XAxis dataKey="label" tick={{ fontSize: 12 }} />
                        <YAxis tick={{ fontSize: 12 }} allowDecimals={false} />
                        <Tooltip />
                        <Legend />
                        <Line type="monotone" dataKey="strongSkills" name="Strong skills" stroke="#16a34a" strokeWidth={2} dot={{ r: 3 }} />
                        <Line type="monotone" dataKey="weakSkills" name="Weak skills" stroke="#dc2626" strokeWidth={2} dot={{ r: 3 }} />
                      </LineChart>
                    </ResponsiveContainer>
                  ) : (
                    <p className="dash-chart-empty">Not enough data to plot growth yet.</p>
                  )}
                </div>
              </div>
            </section>

            {/* 4. Interview History Summary */}
            <section className="dashboard-section">
              <h2 className="dashboard-section-title">
                <MdHistory className="dashboard-section-icon" />
                Interview History
              </h2>
              {progress.history && progress.history.length > 0 ? (
                <div className="dash-history-table-wrap">
                  <table className="dash-history-table">
                    <thead>
                      <tr>
                        <th>Date</th>
                        <th>Role</th>
                        <th>Difficulty</th>
                        <th>Score</th>
                      </tr>
                    </thead>
                    <tbody>
                      {progress.history.map((h) => (
                        <tr
                          key={h.interviewId}
                          className="dash-history-row"
                          onClick={() =>
                            navigate(h.interviewId ? `/feedback/${h.interviewId}` : '/history')
                          }
                        >
                          <td>{h.date}</td>
                          <td>{h.type || '—'}</td>
                          <td>{h.difficulty || '—'}</td>
                          <td>
                            <span className="dash-history-score" style={{ color: scoreColor(h.score) }}>
                              {h.score != null ? h.score.toFixed(1) : '—'}
                            </span>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="dash-chart-empty">No interview history to show.</p>
              )}
            </section>
          </>
        )}
      </div>
    </div>
  );
}

export default DashboardPage;
