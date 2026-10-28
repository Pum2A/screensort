export type TreeBox = {left: number, top: number, width: number, height: number}

// Kliknięcie paddingu lub paska przewijania wewnątrz okna nie zamyka podglądu.
export function isOutsideBox(point: {x: number, y: number}, box: TreeBox): boolean {
    return point.x < box.left || point.x > box.left + box.width
        || point.y < box.top || point.y > box.top + box.height
}

// Współrzędne są względem drzewka. Linie prowadzą przez odstępy, nie przez treść gałęzi.
export function treeConnections(preview: TreeBox, branches: TreeBox[]): string[] {
    const paths: string[] = []
    for (const branch of branches) {
        const targetY = branch.top + 22
        if (branch.left > preview.left + preview.width) {
            const trunkX = preview.left + preview.width + 12
            const gateX = branch.left - 12
            const gateY = branch.top - 12
            paths.push(`M ${preview.left + preview.width} ${preview.top + 22} H ${trunkX} V ${gateY} H ${gateX} V ${targetY} H ${branch.left}`)
        } else {
            const trunkX = Math.min(preview.left, branch.left) - 12
            paths.push(`M ${preview.left} ${preview.top + 22} H ${trunkX} V ${targetY} H ${branch.left}`)
        }
    }
    return paths
}
