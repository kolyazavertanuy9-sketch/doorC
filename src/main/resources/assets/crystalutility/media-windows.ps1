$ErrorActionPreference = 'Stop'
try {
    Add-Type -AssemblyName System.Runtime.WindowsRuntime
    $managerType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
    $sessionType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSession, Windows.Media.Control, ContentType=WindowsRuntime]
    $mediaPropertiesType = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType=WindowsRuntime]

    function Await-WinRt($operation, $resultType) {
        $method = [System.WindowsRuntimeSystemExtensions].GetMethods() |
            Where-Object {
                $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and
                $_.GetGenericArguments().Count -eq 1 -and
                $_.GetParameters().Count -eq 1 -and
                $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            } | Select-Object -First 1
        if ($null -eq $method) { throw "Could not locate the WinRT IAsyncOperation adapter." }
        $task = $method.MakeGenericMethod($resultType).Invoke($null, @($operation))
        $task.GetAwaiter().GetResult()
    }

    function Read-Thumbnail($thumbnail) {
        if ($null -eq $thumbnail) { return "" }
        $randomStream = $null
        $reader = $null
        $memory = $null
        try {
            $streamType = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage, ContentType=WindowsRuntime]
            $randomStream = Await-WinRt $thumbnail.OpenReadAsync() $streamType
            $streamInterface = $streamType.GetInterfaces() |
                Where-Object { $_.FullName -eq 'Windows.Storage.Streams.IRandomAccessStream' } |
                Select-Object -First 1
            if ($null -eq $streamInterface) { throw "Thumbnail stream interface is unavailable." }
            $inputStream = $streamInterface.GetMethod('GetInputStreamAt').Invoke($randomStream, @([uint64]0))
            $script:thumbnailSize = [int64]$randomStream.Size
            $reader = [Windows.Storage.Streams.DataReader]::new($inputStream)
            $memory = [System.IO.MemoryStream]::new()
            while ($memory.Length -lt 16777216) {
                $request = [uint32][Math]::Min(65536, 16777216 - $memory.Length)
                $loaded = Await-WinRt $reader.LoadAsync($request) ([uint32])
                if ($loaded -le 0) { break }
                $buffer = [byte[]]::new([int]$loaded)
                $reader.ReadBytes($buffer)
                $memory.Write($buffer, 0, $buffer.Length)
                $script:thumbnailLoaded += [int64]$loaded
                if ($loaded -lt $request) { break }
            }
            if ($memory.Length -le 0 -or $memory.Length -ge 16777216) {
                $script:thumbnailEmpty++
                return ""
            }
            $script:thumbnailBytes += $memory.Length
            return [Convert]::ToBase64String($memory.ToArray())
        } catch {
            $script:thumbnailErrors++
            [Console]::Error.WriteLine("wvDoorClient media thumbnail: " + $_.Exception.Message)
            return ""
        } finally {
            if ($null -ne $memory) { $memory.Dispose() }
        }
    }

    $operation = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()
    $manager = Await-WinRt $operation $managerType
    $currentSession = $manager.GetCurrentSession()
    $sessions = @($manager.GetSessions())
    if ($null -eq $currentSession -and $sessions.Count -eq 0) { exit 0 }

    $orderedSessions = @()
    foreach ($candidate in $sessions) {
        if ([string]$candidate.GetPlaybackInfo().PlaybackStatus -eq "Playing") {
            $orderedSessions += $candidate
        }
    }
    if ($null -ne $currentSession) { $orderedSessions += $currentSession }
    foreach ($candidate in $sessions) {
        if ($null -eq $currentSession -or $candidate.SourceAppUserModelId -ne $currentSession.SourceAppUserModelId) {
            $orderedSessions += $candidate
        }
    }

    $session = $null
    $properties = $null
    $playback = $null
    $artwork = ""
    $thumbnailCount = 0
    $script:thumbnailBytes = 0
    $script:thumbnailErrors = 0
    $script:thumbnailSize = 0
    $script:thumbnailLoaded = 0
    $script:thumbnailEmpty = 0
    foreach ($candidate in $orderedSessions) {
        $candidateProperties = Await-WinRt $candidate.TryGetMediaPropertiesAsync() $mediaPropertiesType
        if ($null -ne $candidateProperties.Thumbnail) { $thumbnailCount++ }
        $candidateArtwork = Read-Thumbnail $candidateProperties.Thumbnail
        if ($null -eq $session) {
            $session = $candidate
            $properties = $candidateProperties
            $playback = $candidate.GetPlaybackInfo()
        }
        if (-not [string]::IsNullOrWhiteSpace($candidateArtwork)) {
            $session = $candidate
            $properties = $candidateProperties
            $playback = $candidate.GetPlaybackInfo()
            $artwork = $candidateArtwork
            break
        }
    }
    if ($null -eq $session) { exit 0 }

    $timeline = $session.GetTimelineProperties()
    $title = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$properties.Title))
    $artist = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes([string]$properties.Artist))
    $position = [int64]($timeline.Position - $timeline.StartTime).TotalMilliseconds
    $duration = [int64]($timeline.EndTime - $timeline.StartTime).TotalMilliseconds
    $position = [Math]::Max(0, $position)
    $duration = [Math]::Max(0, $duration)
    if ($duration -gt 0) { $position = [Math]::Min($position, $duration) }
    $state = [string]$playback.PlaybackStatus
    $diagnostics = "sessions=$($sessions.Count);candidates=$($orderedSessions.Count);thumbnails=$thumbnailCount;size=$script:thumbnailSize;loaded=$script:thumbnailLoaded;bytes=$script:thumbnailBytes;empty=$script:thumbnailEmpty;errors=$script:thumbnailErrors"
    Write-Output "$title|$artist|$position|$duration|$state|$artwork|$diagnostics"
} catch {
    [Console]::Error.WriteLine("wvDoorClient media bridge: " + $_.Exception.Message)
    exit 1
}
