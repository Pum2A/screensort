//! Startup stage durations, one line per launch in `startup-timings.log` in the app data folder.
//! Only stage names, milliseconds and the outcome: never paths, ports or tokens.

use std::{
    fs,
    path::Path,
    time::{Instant, SystemTime, UNIX_EPOCH},
};

const FILE_NAME: &str = "startup-timings.log";
const KEPT_LAUNCHES: usize = 50;

pub struct StartupTimer {
    kind: &'static str,
    started: Instant,
    last: Instant,
    stages: Vec<(&'static str, u128)>,
}

impl StartupTimer {
    /// `kind` tells the two starts apart in the file: "app" (database and backend) or "ai".
    pub fn start(kind: &'static str) -> Self {
        let now = Instant::now();
        Self {
            kind,
            started: now,
            last: now,
            stages: Vec::new(),
        }
    }

    /// Ends the stage that ran since the previous mark.
    pub fn mark(&mut self, stage: &'static str) {
        let now = Instant::now();
        self.stages
            .push((stage, now.duration_since(self.last).as_millis()));
        self.last = now;
    }

    pub fn line(&self, outcome: &str, unix_seconds: u64, now: Instant) -> String {
        let mut line = format!("{unix_seconds} {} outcome={outcome}", self.kind);
        for (stage, millis) in &self.stages {
            line.push_str(&format!(" {stage}={millis}ms"));
        }
        // Time after the last mark is the stage that was still running when the start failed.
        if outcome != "ok" {
            line.push_str(&format!(
                " unfinished={}ms",
                now.duration_since(self.last).as_millis()
            ));
        }
        // Counted to the moment of saving, so a failed start includes its unfinished stage.
        line.push_str(&format!(
            " total={}ms",
            now.duration_since(self.started).as_millis()
        ));
        line
    }

    /// Best effort: a failed write never blocks the start.
    pub fn save(&self, data: &Path, outcome: &str) {
        let now = Instant::now();
        let unix_seconds = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|time| time.as_secs())
            .unwrap_or(0);
        let file = data.join(FILE_NAME);
        let previous = fs::read_to_string(&file).unwrap_or_default();
        let _ = fs::write(
            &file,
            keep_recent(&previous, &self.line(outcome, unix_seconds, now)),
        );
    }
}

fn keep_recent(previous: &str, line: &str) -> String {
    let mut lines: Vec<&str> = previous.lines().filter(|line| !line.is_empty()).collect();
    lines.push(line);
    let skip = lines.len().saturating_sub(KEPT_LAUNCHES);
    let mut text = lines[skip..].join("\n");
    text.push('\n');
    text
}

#[cfg(test)]
mod tests {
    use super::{keep_recent, StartupTimer, KEPT_LAUNCHES};
    use std::time::Duration;

    #[test]
    fn a_line_lists_each_stage_in_order_and_the_total() {
        let mut timer = StartupTimer::start("app");
        timer.mark("database");
        timer.mark("backend");
        let line = timer.line("ok", 1_700_000_000, timer.last);
        assert!(line.starts_with("1700000000 app outcome=ok database="));
        assert!(!line.contains("unfinished"));
        let backend = line.find(" backend=").unwrap();
        let total = line.find(" total=").unwrap();
        assert!(line.find(" database=").unwrap() < backend && backend < total);
        assert!(line.ends_with("ms"));
    }

    #[test]
    fn only_the_most_recent_launches_are_kept() {
        let previous: String = (0..KEPT_LAUNCHES + 5)
            .map(|index| format!("{index} outcome=ok\n"))
            .collect();
        let text = keep_recent(&previous, "new outcome=ok");
        let lines: Vec<&str> = text.lines().collect();
        assert_eq!(lines.len(), KEPT_LAUNCHES);
        assert_eq!(lines.last(), Some(&"new outcome=ok"));
        assert_eq!(lines.first(), Some(&"6 outcome=ok"));
    }

    #[test]
    fn saving_writes_into_the_data_folder() {
        let data = tempfile::tempdir().unwrap();
        let mut timer = StartupTimer::start("app");
        timer.mark("prepare");
        timer.save(data.path(), "failed");
        let text = std::fs::read_to_string(data.path().join("startup-timings.log")).unwrap();
        assert!(text.contains("app outcome=failed prepare="));
        assert!(!text.contains(&data.path().display().to_string()));
    }

    #[test]
    fn a_failed_start_keeps_the_time_of_the_stage_that_never_finished() {
        let mut timer = StartupTimer::start("app");
        timer.mark("prepare");
        let later = timer.last + Duration::from_secs(60);
        let line = timer.line("failed", 1_700_000_000, later);
        assert!(line.contains(" prepare="));
        assert!(line.contains(" unfinished=60000ms"));
        let total = later.duration_since(timer.started).as_millis();
        assert!(line.ends_with(&format!(" total={total}ms")));
    }

    #[test]
    fn the_ai_start_has_its_own_kind_of_line() {
        let mut timer = StartupTimer::start("ai");
        timer.mark("spawn");
        timer.mark("models");
        timer.mark("connect");
        let line = timer.line("ok", 1_700_000_000, timer.last);
        assert!(line.starts_with("1700000000 ai outcome=ok spawn="));
        assert!(line.contains(" models=") && line.contains(" connect="));
    }
}
