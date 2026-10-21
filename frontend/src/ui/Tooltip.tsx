import type {ReactElement, ReactNode} from 'react'
import * as Primitive from '@radix-ui/react-tooltip'
import './tooltip.css'

export function TooltipProvider({children}: {children: ReactNode}) {
    return <Primitive.Provider delayDuration={400} skipDelayDuration={150}>{children}</Primitive.Provider>
}

export function Tooltip({label, children}: {label: string; children: ReactElement}) {
    return <Primitive.Root>
        <Primitive.Trigger asChild>{children}</Primitive.Trigger>
        <Primitive.Portal><Primitive.Content className="ui-tooltip" sideOffset={8} collisionPadding={12}>
            {label}<Primitive.Arrow className="ui-tooltip-arrow"/>
        </Primitive.Content></Primitive.Portal>
    </Primitive.Root>
}
