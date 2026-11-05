import { Link, useOutletContext } from 'react-router-dom';
import { hasEffectiveAllPermissions, hasEffectiveAnyPermission, hasRecoveryCapability } from '../utils/effectivePermissions';

export default function PermissionGuard({ any = [], all = [], recovery = false, children }) {
    const context = useOutletContext() || {};
    const permitted = !context.permissionsUnavailable && (recovery
        ? hasRecoveryCapability(context.effectivePermissions)
        : hasEffectiveAnyPermission(context.effectivePermissions, any)
            && hasEffectiveAllPermissions(context.effectivePermissions, all));

    if (permitted) return children;
    return <main className="mx-auto mt-10 max-w-2xl rounded-2xl border border-gray-800 bg-gray-900/80 p-6 sm:p-8" role="alert">
        <p className="text-xs font-semibold uppercase tracking-[0.16em] text-amber-300">Access restricted</p>
        <h1 className="mt-2 text-xl font-semibold text-white">You do not have permission to view this page</h1>
        <p className="mt-2 text-sm leading-relaxed text-gray-400">Page access is based on your current role permissions. If you need access, contact an administrator. The backend independently checks every request.</p>
        <Link to="/dashboard" className="mt-5 inline-flex rounded-lg border border-gray-700 px-4 py-2.5 text-sm font-medium text-gray-200 hover:bg-gray-800">Return to dashboard</Link>
    </main>;
}
