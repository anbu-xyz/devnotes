@import java.util.Map
@import uk.anbu.devnotes.controller.ExchangeRateController.UploadResult
@param Map<String, Double> rates
@param String errorMessage
@param String successMessage
@param UploadResult uploadResult

<!DOCTYPE html>
<html lang="en">
<head>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/5.15.3/css/all.min.css">
    <link rel="stylesheet" type="text/css" href="/css/style.css">
    <title>Exchange Rate Manager</title>
</head>
<body class="p-8">

<h1><i class="fas fa-dollar-sign"></i> Exchange Rate Manager</h1>

@if(errorMessage != null)
    <div class="encryption-error">
        <i class="fas fa-exclamation-triangle"></i> ${errorMessage}
    </div>
@endif

@if(successMessage != null)
    <div class="enc-key-active">
        <i class="fas fa-check-circle"></i> ${successMessage}
    </div>
@endif

@if(uploadResult != null)
    <div class="${uploadResult.errors().isEmpty() ? "enc-key-active" : "enc-key-needed"}">
        @if(uploadResult.imported() > 0)
            <p><i class="fas fa-check-circle"></i> Successfully imported <strong>${uploadResult.imported()}</strong> rate(s).</p>
        @endif
        @if(!uploadResult.errors().isEmpty())
            <p><i class="fas fa-exclamation-triangle"></i> ${uploadResult.errors().size()} row(s) could not be imported:</p>
            <ul>
                @for(String err : uploadResult.errors())
                    <li>${err}</li>
                @endfor
            </ul>
        @endif
        @if(uploadResult.imported() == 0 && uploadResult.errors().isEmpty())
            <p>The file contained no data rows.</p>
        @endif
    </div>
@endif

@if(rates.isEmpty())
    <p class="muted">No rates configured yet.</p>
@else
    <table class="db-meta-table">
        <thead>
        <tr>
            <th>Pair</th>
            <th>Rate</th>
            <th>Action</th>
        </tr>
        </thead>
        <tbody>
        @for(var entry : rates.entrySet())
            <tr>
                <td>${entry.getKey()}</td>
                <td>${String.format("%.6f", entry.getValue())}</td>
                <td>
                    <form action="/tools/exchange-rates/delete" method="post" style="margin:0">
                        <input type="hidden" name="pair" value="${entry.getKey()}">
                        <button type="submit" class="btn-danger" title="Delete">
                            <i class="fas fa-trash-alt"></i>
                        </button>
                    </form>
                </td>
            </tr>
        @endfor
        </tbody>
    </table>
@endif

<hr>

<h2>Add / Update Rate</h2>
<form action="/tools/exchange-rates" method="post" class="config-form">
    <div class="form-group">
        <label for="pair">Currency Pair:</label>
        <input type="text" id="pair" name="pair" class="config-input"
               placeholder="GBP/USD" pattern="[A-Za-z]{3}/[A-Za-z]{3}"
               required>
    </div>
    <div class="form-group">
        <label for="rate">Rate:</label>
        <input type="number" id="rate" name="rate" class="config-input"
               placeholder="1.2700" step="any" min="0.000001"
               required>
    </div>
    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-plus"></i>Add Rate
        </button>
    </div>
</form>

<hr>

<h2>Bulk Upload from CSV</h2>
<p class="muted">Upload a CSV file with two columns: <code>currency-pair</code> and <code>rate</code> (e.g. <code>GBP/USD,1.2700</code>). An optional header row is automatically detected and skipped.</p>
<form action="/tools/exchange-rates/upload" method="post" enctype="multipart/form-data" class="config-form">
    <div class="form-group">
        <label for="file">CSV File:</label>
        <input type="file" id="file" name="file" class="config-input" accept=".csv,text/csv,text/plain" required>
    </div>
    <div class="form-actions">
        <button type="submit" class="btn-blue-glow">
            <i class="fas fa-upload"></i>Upload
        </button>
    </div>
</form>

<hr>
<a href="/tools" class="btn-blue-glow"><i class="fas fa-arrow-left"></i>Back to Tools</a>

</body>
</html>

