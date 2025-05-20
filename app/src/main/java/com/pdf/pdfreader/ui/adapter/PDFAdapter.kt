package com.pdf.pdfreader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.pdf.pdfreader.databinding.RawPdfBinding
import com.pdf.pdfreader.extension.eLog
import com.pdf.pdfreader.utiles.PdfFileDetails

class PDFAdapter(
    private val list: ArrayList<PdfFileDetails>,
    private val callback: (PdfFileDetails) -> Unit
) :
    RecyclerView.Adapter<PDFAdapter.PDFViewHolder>() {
    inner class PDFViewHolder(private val binding: RawPdfBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(pdfFileDetails: PdfFileDetails) {
//            eLog(pdfFileDetails.toString())
            binding.textViewFileName.text = pdfFileDetails.fileName
            binding.textViewFileDate.text = pdfFileDetails.filePath.toString()
            binding.root.setOnClickListener {
                callback(pdfFileDetails)
            }
        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PDFViewHolder {
        return PDFViewHolder(
            RawPdfBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: PDFViewHolder, position: Int) {
        holder.bind(list[position])
    }
}