import { useState } from 'react';
import { BarChart3, Clock, Eye, Store } from 'lucide-react';
import { useApp } from './context/AppContext';
import { authRepository } from './data/repositories/authRepository';
import { BrandLogoBox } from './presentation/components/CommonComponents';
import { FloatingInstallButton } from './presentation/components/FloatingInstallButton';
import { SyncLogsModal } from './presentation/components/SyncLogsModal';
import { AdminScreen } from './presentation/screens/AdminScreen';
import { HostersManagementScreen } from './presentation/screens/HostersManagementScreen';
import { InspectionScreen } from './presentation/screens/InspectionScreen';
import { LoginScreen } from './presentation/screens/LoginScreen';
import { ReceptionScreen } from './presentation/screens/ReceptionScreen';
import { ShiftScreen } from './presentation/screens/ShiftScreen';

type AppTab = 'RECEPCAO' | 'INSPECAO' | 'ADMINISTRACAO' | 'TURNO';

const TABS: {
  id: AppTab;
  label: string;
  testId: string;
  Icon: typeof Store;
}[] = [
  { id: 'RECEPCAO', label: 'Recepção', testId: 'tab_recepcao', Icon: Store },
  { id: 'INSPECAO', label: 'Inspeção', testId: 'tab_inspecao', Icon: Eye },
  { id: 'ADMINISTRACAO', label: 'Administração', testId: 'tab_admin', Icon: BarChart3 },
  { id: 'TURNO', label: 'Turno', testId: 'tab_turno', Icon: Clock },
];

export default function App() {
  const { ready, currentHoster, showSyncLogsModal, setShowSyncLogsModal, snackbarMessage } =
    useApp();
  const [selectedTab, setSelectedTab] = useState<AppTab>('RECEPCAO');

  return (
    <div className="app-viewport-shell">
      <div className="app-device-frame">
        {!ready ? (
          <div
            style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
              justifyContent: 'center',
              gap: '14px',
            }}
          >
            <BrandLogoBox size={88} radius={22} padding={8} />
            <div style={{ fontSize: '14px', fontWeight: 600, color: 'var(--pao-anthracite-muted)' }}>
              Carregando Sistema de Recepção...
            </div>
          </div>
        ) : !currentHoster ? (
          <LoginScreen />
        ) : currentHoster.isAdmin ? (
          <HostersManagementScreen onLogout={() => authRepository.logout()} />
        ) : (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            {/* Active Screen Content */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
              {selectedTab === 'RECEPCAO' && <ReceptionScreen />}
              {selectedTab === 'INSPECAO' && <InspectionScreen />}
              {selectedTab === 'ADMINISTRACAO' && <AdminScreen />}
              {selectedTab === 'TURNO' && <ShiftScreen />}
            </div>

            {/* Persistent Bottom Navigation Bar matching Material 3 MainActivity.kt */}
            <nav
              data-testid="persistent_bottom_nav"
              style={{
                width: '100%',
                backgroundColor: '#FFFFFF',
                borderTop: '1px solid var(--pao-border-light)',
                borderLeft: '1px solid var(--pao-border-light)',
                borderRight: '1px solid var(--pao-border-light)',
                borderTopLeftRadius: '16px',
                borderTopRightRadius: '16px',
                boxShadow: '0 -4px 16px rgba(38, 29, 28, 0.06)',
                paddingTop: '8px',
                paddingBottom: 'calc(8px + var(--safe-bottom))',
                paddingLeft: '6px',
                paddingRight: '6px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-around',
                flexShrink: 0,
              }}
            >
              {TABS.map(({ id, label, testId, Icon }) => {
                const isSelected = selectedTab === id;
                return (
                  <button
                    key={id}
                    type="button"
                    onClick={() => setSelectedTab(id)}
                    data-testid={testId}
                    className="tap-feedback"
                    style={{
                      flex: 1,
                      border: 'none',
                      background: 'transparent',
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '3px',
                      padding: '2px 0',
                      minHeight: '48px',
                    }}
                  >
                    <div
                      style={{
                        width: '56px',
                        height: '30px',
                        borderRadius: '15px',
                        backgroundColor: isSelected ? 'var(--pao-cream)' : 'transparent',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        transition: 'background-color 0.15s ease',
                      }}
                    >
                      <Icon
                        size={21}
                        color={isSelected ? '#9C1819' : '#6B5855'}
                        strokeWidth={isSelected ? 2.3 : 1.9}
                      />
                    </div>
                    <span
                      style={{
                        fontSize: '11px',
                        fontWeight: isSelected ? 700 : 500,
                        color: isSelected ? 'var(--pao-red)' : 'var(--pao-anthracite-muted)',
                      }}
                    >
                      {label}
                    </span>
                  </button>
                );
              })}
            </nav>
          </div>
        )}

        {/* Global Snackbar Toast */}
        {snackbarMessage && (
          <div
            role="status"
            style={{
              position: 'absolute',
              bottom: '84px',
              left: '16px',
              right: '16px',
              backgroundColor: 'var(--pao-anthracite)',
              color: '#FFFFFF',
              borderRadius: '12px',
              padding: '12px 16px',
              fontSize: '13px',
              fontWeight: 600,
              boxShadow: '0 8px 24px rgba(38, 29, 28, 0.25)',
              zIndex: 90,
              animation: 'fadeIn 0.15s ease-out',
            }}
          >
            {snackbarMessage}
          </div>
        )}

        {/* Floating App Download/Install Corner Pill */}
        {ready && (
          <FloatingInstallButton
            hasBottomBar={Boolean(currentHoster && !currentHoster.isAdmin)}
          />
        )}

        {/* Global Sync Logs Modal */}
        {showSyncLogsModal && (
          <SyncLogsModal onDismiss={() => setShowSyncLogsModal(false)} />
        )}
      </div>
    </div>
  );
}
