import { useEffect, useState, type CSSProperties } from 'react'

type AnimatedHeadingProps = {
  text: string
  className?: string
  style?: CSSProperties
  initialDelay?: number
  charDelay?: number
}

export function AnimatedHeading({
  text,
  className = '',
  style,
  initialDelay = 200,
  charDelay = 30,
}: AnimatedHeadingProps) {
  const [started, setStarted] = useState(false)
  const lines = text.split('\n')

  useEffect(() => {
    const timer = window.setTimeout(() => setStarted(true), initialDelay)
    return () => window.clearTimeout(timer)
  }, [initialDelay])

  return (
    <h1 className={className} style={style}>
      {lines.map((line, lineIndex) => {
        const lineLength = line.length
        return (
          <span key={lineIndex} className="block">
            {Array.from(line).map((char, charIndex) => {
              const delay =
                lineIndex * lineLength * charDelay + charIndex * charDelay
              const display = char === ' ' ? '\u00A0' : char
              return (
                <span
                  key={`${lineIndex}-${charIndex}`}
                  className="inline-block transition-all"
                  style={{
                    opacity: started ? 1 : 0,
                    transform: started ? 'translateX(0)' : 'translateX(-18px)',
                    transitionDuration: '500ms',
                    transitionDelay: `${delay}ms`,
                  }}
                >
                  {display}
                </span>
              )
            })}
          </span>
        )
      })}
    </h1>
  )
}
