package main

import (
    "encoding/json"
    "errors"
    "fmt"
    "os"
    "path/filepath"
    "strconv"
    "strings"

    "github.com/amnezia-vpn/amnezia-libxray/nodep"
)

type meta struct {
    Protocol string `json:"protocol"`
    Name     string `json:"name"`
    Host     string `json:"host"`
}

func main() {
    if len(os.Args) < 2 {
        fail("usage: vmesspro-helper <build|normalize|meta> ...")
    }
    var err error
    switch strings.ToLower(os.Args[1]) {
    case "build":
        if len(os.Args) < 6 {
            fail("build requires: input output socksPort httpPort [endpointIP]")
        }
        socksPort, e1 := strconv.Atoi(os.Args[4])
        httpPort, e2 := strconv.Atoi(os.Args[5])
        if e1 != nil || e2 != nil {
            fail("invalid local port")
        }
        endpointIP := ""
        if len(os.Args) >= 7 {
            endpointIP = strings.TrimSpace(os.Args[6])
        }
        err = buildConfig(os.Args[2], os.Args[3], socksPort, httpPort, endpointIP)
    case "normalize":
        if len(os.Args) != 4 {
            fail("normalize requires: input output")
        }
        err = normalize(os.Args[2], os.Args[3])
    case "meta":
        if len(os.Args) != 3 {
            fail("meta requires: input")
        }
        var m meta
        m, err = metadata(os.Args[2])
        if err == nil {
            encoded, marshalErr := json.Marshal(m)
            if marshalErr != nil {
                err = marshalErr
            } else {
                fmt.Print(string(encoded))
            }
        }
    default:
        err = fmt.Errorf("unknown command: %s", os.Args[1])
    }
    if err != nil {
        fail(err.Error())
    }
}

func fail(message string) {
    fmt.Fprintln(os.Stderr, message)
    os.Exit(1)
}

func convert(inputPath string) (map[string]any, string, error) {
    dir := filepath.Dir(inputPath)
    temp, err := os.CreateTemp(dir, "vmesspro-converted-*.json")
    if err != nil {
        return nil, "", err
    }
    tempPath := temp.Name()
    _ = temp.Close()
    _ = os.Remove(tempPath)

    if err := nodep.ConvertShareTextToXrayJson(inputPath, tempPath); err != nil {
        return nil, tempPath, err
    }
    raw, err := os.ReadFile(tempPath)
    if err != nil {
        return nil, tempPath, err
    }
    var root map[string]any
    if err := json.Unmarshal(raw, &root); err != nil {
        return nil, tempPath, err
    }
    return root, tempPath, nil
}

func normalize(inputPath, outputPath string) error {
    root, tempPath, err := convert(inputPath)
    _ = root
    if tempPath != "" {
        defer os.Remove(tempPath)
    }
    if err != nil {
        return err
    }
    if err := nodep.ConvertXrayJsonToShareText(tempPath, outputPath); err != nil {
        return err
    }
    return nil
}

func metadata(inputPath string) (meta, error) {
    root, tempPath, err := convert(inputPath)
    if tempPath != "" {
        defer os.Remove(tempPath)
    }
    if err != nil {
        return meta{}, err
    }
    first, err := firstOutbound(root)
    if err != nil {
        return meta{}, err
    }
    protocol, _ := first["protocol"].(string)
    name, _ := first["name"].(string)
    if name == "" {
        name, _ = first["tag"].(string)
    }
    host := outboundHost(first)
    if name == "" {
        if host != "" {
            name = strings.ToUpper(protocol) + " • " + host
        } else {
            name = strings.ToUpper(protocol)
        }
    }
    return meta{Protocol: protocol, Name: name, Host: host}, nil
}

func buildConfig(inputPath, outputPath string, socksPort, httpPort int, endpointIP string) error {
    root, tempPath, err := convert(inputPath)
    if tempPath != "" {
        defer os.Remove(tempPath)
    }
    if err != nil {
        return err
    }
    first, err := firstOutbound(root)
    if err != nil {
        return err
    }

    first["tag"] = "proxy"
    if endpointIP != "" {
        replaceOutboundHost(first, endpointIP)
    }

    root["outbounds"] = []any{
        first,
        map[string]any{"protocol": "freedom", "tag": "direct"},
        map[string]any{"protocol": "blackhole", "tag": "block"},
    }
    root["inbounds"] = []any{
        map[string]any{
            "listen":   "127.0.0.1",
            "port":     socksPort,
            "protocol": "socks",
            "tag":      "socks-in",
            "settings": map[string]any{"udp": true, "auth": "noauth"},
            "sniffing": map[string]any{"enabled": true, "destOverride": []any{"http", "tls", "quic"}},
        },
        map[string]any{
            "listen":   "127.0.0.1",
            "port":     httpPort,
            "protocol": "http",
            "tag":      "http-in",
            "settings": map[string]any{},
            "sniffing": map[string]any{"enabled": true, "destOverride": []any{"http", "tls"}},
        },
    }
    root["log"] = map[string]any{"loglevel": "warning"}
    root["routing"] = map[string]any{
        "domainStrategy": "AsIs",
        "rules": []any{
            map[string]any{
                "type":        "field",
                "inboundTag":  []any{"socks-in", "http-in"},
                "outboundTag": "proxy",
            },
        },
    }

    encoded, err := json.MarshalIndent(root, "", "  ")
    if err != nil {
        return err
    }
    if err := os.MkdirAll(filepath.Dir(outputPath), 0o755); err != nil {
        return err
    }
    return os.WriteFile(outputPath, encoded, 0o600)
}

func firstOutbound(root map[string]any) (map[string]any, error) {
    raw, ok := root["outbounds"].([]any)
    if !ok || len(raw) == 0 {
        return nil, errors.New("no outbound in converted Xray config")
    }
    first, ok := raw[0].(map[string]any)
    if !ok {
        return nil, errors.New("invalid outbound object")
    }
    return first, nil
}

func outboundHost(outbound map[string]any) string {
    settings, _ := outbound["settings"].(map[string]any)
    if settings == nil {
        return ""
    }
    if vnext, ok := settings["vnext"].([]any); ok && len(vnext) > 0 {
        if server, ok := vnext[0].(map[string]any); ok {
            if value, ok := server["address"].(string); ok {
                return value
            }
        }
    }
    if servers, ok := settings["servers"].([]any); ok && len(servers) > 0 {
        if server, ok := servers[0].(map[string]any); ok {
            if value, ok := server["address"].(string); ok {
                return value
            }
        }
    }
    return ""
}

func replaceOutboundHost(outbound map[string]any, endpointIP string) {
    settings, _ := outbound["settings"].(map[string]any)
    if settings == nil {
        return
    }
    if vnext, ok := settings["vnext"].([]any); ok && len(vnext) > 0 {
        if server, ok := vnext[0].(map[string]any); ok {
            server["address"] = endpointIP
            return
        }
    }
    if servers, ok := settings["servers"].([]any); ok && len(servers) > 0 {
        if server, ok := servers[0].(map[string]any); ok {
            server["address"] = endpointIP
        }
    }
}
