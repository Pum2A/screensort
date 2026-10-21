import {LazyMotion, MotionConfig, domAnimation, useReducedMotion} from 'motion/react'
import type {ReactNode} from 'react'

// Small animation feature set. No drag/physics/graph engine in the initial bundle.
export function MotionProvider({children}: {children: ReactNode}) {
    const reduce = useReducedMotion()
    return <LazyMotion features={domAnimation} strict>
        <MotionConfig reducedMotion="user" transition={{duration: reduce ? 0 : 0.22, ease: [0.22, 1, 0.36, 1]}}>
            {children}
        </MotionConfig>
    </LazyMotion>
}
