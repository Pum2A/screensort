/** 1 obraz, 2-4 obrazy, 5 obrazów, 12 obrazów, 22 obrazy. */
export function imagesLabel(count: number): string {
    const tens = count % 100, ones = count % 10
    if (count === 1) return '1 obraz'
    if (ones >= 2 && ones <= 4 && (tens < 12 || tens > 14)) return `${count} obrazy`
    return `${count} obrazów`
}
