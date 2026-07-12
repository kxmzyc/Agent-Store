$ErrorActionPreference = 'Stop'

$dataPath = Join-Path $PSScriptRoot 'selfcheck_ppt_data.json'
$data = Get-Content -Raw -Encoding UTF8 $dataPath | ConvertFrom-Json
$sourcePath = Join-Path $PSScriptRoot $data.sourceFile
$outputPath = Join-Path $PSScriptRoot $data.outputFile
$pdfPath = Join-Path $PSScriptRoot $data.pdfFile
$previewDir = Join-Path $PSScriptRoot $data.previewDir

New-Item -ItemType Directory -Force (Split-Path -Parent $outputPath) | Out-Null
New-Item -ItemType Directory -Force $previewDir | Out-Null
Copy-Item -LiteralPath $sourcePath -Destination $outputPath -Force

function Get-OfficeRgb {
    param([int] $Red, [int] $Green, [int] $Blue)
    return $Red + (256 * $Green) + (65536 * $Blue)
}

function Set-ShapeText {
    param(
        [Parameter(Mandatory = $true)] $Shape,
        [Parameter(Mandatory = $true)] [AllowEmptyString()] [string] $Text,
        [double] $FontSize = 0
    )

    $normalized = $Text -replace "`r?`n", "`r"
    $Shape.TextFrame.TextRange.Text = $normalized
    $Shape.TextFrame.TextRange.Font.Name = 'Microsoft YaHei'
    $Shape.TextFrame.TextRange.Font.NameFarEast = 'Microsoft YaHei'
    if ($FontSize -gt 0) {
        $Shape.TextFrame.TextRange.Font.Size = $FontSize
    }
}

function Find-TargetShape {
    param($Slide, $Update)

    if ($null -ne $Update.shapeId) {
        foreach ($shape in $Slide.Shapes) {
            if ($shape.Id -eq [int] $Update.shapeId) {
                return $shape
            }
        }
    }

    if ($null -ne $Update.match) {
        foreach ($shape in $Slide.Shapes) {
            if ($shape.HasTextFrame -eq -1 -and $shape.TextFrame.HasText -eq -1) {
                if ($shape.TextFrame.TextRange.Text -eq [string] $Update.match) {
                    return $shape
                }
            }
        }
    }

    return $null
}

function Get-NotesBody {
    param($Slide)

    foreach ($shape in $Slide.NotesPage.Shapes) {
        try {
            if ($shape.Type -eq 14 -and $shape.PlaceholderFormat.Type -eq 2) {
                return $shape
            }
        }
        catch {
            continue
        }
    }

    return $Slide.NotesPage.Shapes.AddTextbox(1, 36, 360, 648, 240)
}

function Add-ScoreRibbon {
    param($Slide, $Labels)

    $names = @()
    foreach ($shape in $Slide.Shapes) {
        if ($shape.Name -like 'ScoreRibbon*') {
            $names += $shape.Name
        }
    }
    foreach ($name in $names) {
        $Slide.Shapes.Item($name).Delete()
    }

    $band = $Slide.Shapes.AddShape(1, 66, 421, 828, 58)
    $band.Name = 'ScoreRibbonBand'
    $band.Fill.ForeColor.RGB = Get-OfficeRgb 255 255 255
    $band.Line.ForeColor.RGB = Get-OfficeRgb 221 226 234
    $band.Line.Weight = 0.75

    $label = $Slide.Shapes.AddTextbox(1, 84, 439, 66, 18)
    $label.Name = 'ScoreRibbonLabel'
    Set-ShapeText -Shape $label -Text 'SCORE' -FontSize 9.5
    $label.TextFrame.TextRange.Font.Bold = -1
    $label.TextFrame.TextRange.Font.Color.RGB = Get-OfficeRgb 47 70 220

    $startX = 160
    $width = 108
    $gap = 8
    for ($i = 0; $i -lt $Labels.Count; $i++) {
        $chip = $Slide.Shapes.AddShape(5, $startX + (($width + $gap) * $i), 431, $width, 36)
        $chip.Name = "ScoreRibbonChip$($i + 1)"
        $chip.Fill.ForeColor.RGB = Get-OfficeRgb 246 248 252
        $chip.Line.ForeColor.RGB = Get-OfficeRgb 221 226 234
        $chip.Line.Weight = 0.6
        Set-ShapeText -Shape $chip -Text ([string] $Labels[$i]) -FontSize 8.5
        $chip.TextFrame.TextRange.ParagraphFormat.Alignment = 2
        $chip.TextFrame.VerticalAnchor = 3
        $chip.TextFrame.TextRange.Font.Color.RGB = Get-OfficeRgb 55 65 81
    }
}

$ppt = New-Object -ComObject PowerPoint.Application
$ppt.DisplayAlerts = 1

try {
    $presentation = $ppt.Presentations.Open($outputPath, $false, $false, $false)

    foreach ($update in $data.updates) {
        $slide = $presentation.Slides.Item([int] $update.slide)
        $target = Find-TargetShape -Slide $slide -Update $update
        if ($null -eq $target) {
            throw "Could not find target shape on slide $($update.slide)."
        }

        $fontSize = 0
        if ($null -ne $update.fontSize) {
            $fontSize = [double] $update.fontSize
        }
        Set-ShapeText -Shape $target -Text ([string] $update.text) -FontSize $fontSize

        if ($null -ne $update.left) { $target.Left = [double] $update.left }
        if ($null -ne $update.top) { $target.Top = [double] $update.top }
        if ($null -ne $update.width) { $target.Width = [double] $update.width }
        if ($null -ne $update.height) { $target.Height = [double] $update.height }
        if ($null -ne $update.verticalAnchor) { $target.TextFrame.VerticalAnchor = [int] $update.verticalAnchor }

        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($slide) | Out-Null
    }

    $scoreSlide = $presentation.Slides.Item(2)
    Add-ScoreRibbon -Slide $scoreSlide -Labels $data.scoreLabels
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($scoreSlide) | Out-Null

    foreach ($note in $data.notes) {
        $slide = $presentation.Slides.Item([int] $note.slide)
        $body = Get-NotesBody -Slide $slide
        Set-ShapeText -Shape $body -Text ("讲解提示`n" + [string] $note.text) -FontSize 12
        [System.Runtime.InteropServices.Marshal]::ReleaseComObject($slide) | Out-Null
    }

    $presentation.Save()
    $slideCount = $presentation.Slides.Count

    foreach ($slide in $presentation.Slides) {
        $previewPath = Join-Path $previewDir ("slide-{0:D2}.png" -f $slide.SlideIndex)
        $slide.Export($previewPath, 'PNG', 1600, 900)
    }

    $presentation.SaveAs($pdfPath, 32)

    Write-Output "PPTX=$outputPath"
    Write-Output "PDF=$pdfPath"
    Write-Output "SLIDES=$slideCount"

    $presentation.Close()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($presentation) | Out-Null
}
finally {
    $ppt.Quit()
    [System.Runtime.InteropServices.Marshal]::ReleaseComObject($ppt) | Out-Null
    [GC]::Collect()
    [GC]::WaitForPendingFinalizers()
}
