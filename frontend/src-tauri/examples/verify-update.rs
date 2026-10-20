//! Distribution check only: no WebView, app launch, private key or installation.
use base64::{engine::general_purpose::STANDARD, Engine};
use minisign_verify::{PublicKey, Signature};
use std::{fs::File, io::Read};

fn main() -> Result<(), Box<dyn std::error::Error>> {
    let args: Vec<String> = std::env::args().collect();
    if args.len() < 4 {
        return Err(
            "Usage: verify-update installer signature expected-version [--tamper-test]".into(),
        );
    }
    let config: serde_json::Value = serde_json::from_str(include_str!("../tauri.conf.json"))?;
    let key = String::from_utf8(
        STANDARD.decode(
            config["plugins"]["updater"]["pubkey"]
                .as_str()
                .ok_or("No public key")?,
        )?,
    )?;
    let public = PublicKey::decode(&key)?;
    let encoded = std::fs::read_to_string(&args[2])?;
    let signature = Signature::decode(&String::from_utf8(STANDARD.decode(encoded.trim())?)?)?;
    let mut verifier = public.verify_stream(&signature)?;
    let mut file = File::open(&args[1])?;
    let mut buffer = [0u8; 64 * 1024];
    let mut first = true;
    loop {
        let count = file.read(&mut buffer)?;
        if count == 0 {
            break;
        }
        if first && args.get(4).is_some_and(|arg| arg == "--tamper-test") {
            buffer[0] ^= 1;
        }
        first = false;
        verifier.update(&buffer[..count]);
    }
    verifier.finalize()?;
    let version = signature
        .trusted_comment()
        .split('\t')
        .find_map(|field| field.strip_prefix("version:"));
    if version != Some(args[3].as_str()) {
        return Err("Signature is not bound to the expected app version".into());
    }
    println!("Signature and app version verified: {}", args[3]);
    Ok(())
}
