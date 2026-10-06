// Drzewko zajmuje cały rząd, dlatego wstawiamy je dopiero po ostatniej miniaturze tego rzędu.
export function selectedRowEnd(selectedIndex: number, columns: number, fileCount: number): number {
    if (selectedIndex < 0 || selectedIndex >= fileCount || columns < 1) return -1
    return Math.min((Math.floor(selectedIndex / columns) + 1) * columns, fileCount) - 1
}

export type TreeBox = {left: number, top: number, width: number, height: number}

// Współrzędne są względem drzewka. Linie prowadzą przez odstępy, nie przez treść gałęzi.
export function treeConnections(preview: TreeBox, branches: TreeBox[], anchorX: number, anchorY: number): string[] {
    const previewCenter = preview.left + preview.width / 2
    const paths = [`M ${anchorX} ${anchorY} V 12 H ${previewCenter} V ${preview.top}`]
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
