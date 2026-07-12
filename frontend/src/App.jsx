import { Routes, Route, Navigate } from 'react-router-dom';
import LoginPage from './pages/LoginPage';
import Navbar from './components/Navbar';
import ProtectedRoute from './components/ProtectedRoute';
import HomePage from './pages/HomePage';
import InterviewSetupPage from './pages/InterviewSetupPage';
import InterviewPage from './pages/InterviewPage';
import FeedbackPage from './pages/FeedbackPage';
import HistoryPage from './pages/HistoryPage';
import DashboardPage from './pages/DashboardPage';
import CodingIDEPage from './pages/CodingIDEPage';
import { NotificationProvider } from './components/NotificationProvider';
import NotificationPortal from './components/NotificationPortal';

function App() {
  return (
    <NotificationProvider>
      <div className="app-shell">
        <NotificationPortal />
        <Routes>
        <Route path="/login" element={<LoginPage />} />

        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <HomePage />
              </main>
            </ProtectedRoute>
          }
        />
        <Route
          path="/setup"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <InterviewSetupPage />
              </main>
            </ProtectedRoute>
          }
        />
        <Route
          path="/interview/:id"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <InterviewPage />
              </main>
            </ProtectedRoute>
          }
        />
        <Route
          path="/coding-module/:id"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <CodingIDEPage />
              </main>
            </ProtectedRoute>
          }
        />
        <Route
          path="/feedback/:id"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <FeedbackPage />
              </main>
            </ProtectedRoute>
          }
        />
        <Route
          path="/history"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <HistoryPage />
              </main>
            </ProtectedRoute>
          }
        />
        <Route
          path="/dashboard"
          element={
            <ProtectedRoute>
              <Navbar />
              <main className="app-main">
                <DashboardPage />
              </main>
            </ProtectedRoute>
          }
        />

        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
      </div>
    </NotificationProvider>
  );
}

export default App;