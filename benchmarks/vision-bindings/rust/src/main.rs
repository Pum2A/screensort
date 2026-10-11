use ort::session::{Session, builder::GraphOptimizationLevel};
use ort::value::Tensor;
use std::{env, fs, time::Instant};

fn infer(session: &mut Session, tensor: &Tensor<f32>) -> ort::Result<Vec<f32>> {
    let outputs = session.run(ort::inputs![tensor])?;
    Ok(outputs[0].try_extract_tensor::<f32>()?.1.to_vec())
}

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = env::args().collect();
    let threads: usize = args[3].parse()?;
    let warmup: usize = args[4].parse()?;
    let iterations: usize = args[5].parse()?;
    // ORT_DYLIB_PATH selects the identical DLL also used by Java and Python.
    ort::init().commit();
    let start = Instant::now();
    let mut session = Session::builder()?
        .with_intra_threads(threads)?
        .with_inter_threads(1)?
        .with_parallel_execution(false)?
        .with_optimization_level(GraphOptimizationLevel::Level3)?
        .commit_from_file(&args[1])?;
    let load_ms = start.elapsed().as_secs_f64() * 1000.0;
    let raw = fs::read(&args[2])?;
    let count = 3 * 224 * 224;
    let tensors = raw
        .chunks_exact(count * 4)
        .map(|bytes| {
            let floats = bytes
                .chunks_exact(4)
                .map(|chunk| f32::from_le_bytes(chunk.try_into().unwrap()))
                .collect::<Vec<_>>();
            Tensor::from_array(([1usize, 3, 224, 224], floats))
        })
        .collect::<ort::Result<Vec<_>>>()?;
    let start = Instant::now();
    let mut output = infer(&mut session, &tensors[0])?;
    let first_ms = start.elapsed().as_secs_f64() * 1000.0;
    println!("{{\"event\":\"ready\",\"load_ms\":{load_ms},\"first_ms\":{first_ms}}}");
    for index in 0..warmup {
        infer(&mut session, &tensors[index % tensors.len()])?;
    }
    let mut times = Vec::with_capacity(iterations);
    for index in 0..iterations {
        let start = Instant::now();
        output = infer(&mut session, &tensors[index % tensors.len()])?;
        times.push(start.elapsed().as_secs_f64() * 1000.0);
    }
    // Build info from the loaded engine, checked against Python by the runner.
    let version = ort::info().replace('\\', "\\\\").replace('"', "\\\"");
    println!(
        "{{\"event\":\"result\",\"engine_build_info\":\"{version}\",\"times_ms\":{times:?},\"output\":{output:?}}}"
    );
    Ok(())
}
