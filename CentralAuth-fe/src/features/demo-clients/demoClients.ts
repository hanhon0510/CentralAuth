export const demoClients = {
  projects: {
    key: 'projects',
    clientId: 'projects-demo',
    name: 'Projects Demo',
    clientName: 'Projects Demo Client',
    description: 'A project workspace relying on CentralAuth for user sessions.',
    publicPath: '/demo/projects',
    protectedPath: '/demo/projects/protected',
    callbackPath: '/demo/projects/callback',
    tokenStorageKey: 'centralauth.demo.projects.token',
    stateStorageKey: 'centralauth.demo.projects.state',
    accentColor: '#1677ff',
  },
  reports: {
    key: 'reports',
    clientId: 'reports-demo',
    name: 'Reports Demo',
    clientName: 'Reports Demo Client',
    description: 'A reporting portal relying on the same CentralAuth server.',
    publicPath: '/demo/reports',
    protectedPath: '/demo/reports/protected',
    callbackPath: '/demo/reports/callback',
    tokenStorageKey: 'centralauth.demo.reports.token',
    stateStorageKey: 'centralauth.demo.reports.state',
    accentColor: '#13a8a8',
  },
} as const

export type DemoClientKey = keyof typeof demoClients
export type DemoClient = (typeof demoClients)[DemoClientKey]

export function getDemoClient(key: DemoClientKey) {
  return demoClients[key]
}
